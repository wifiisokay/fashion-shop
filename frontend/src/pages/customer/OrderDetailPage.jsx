import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useMyOrder } from '../../hooks/useMyOrder';
import { useCancelOrder } from '../../hooks/useCancelOrder';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '../../api/orderApi';
import { reviewApi } from '../../api/reviewApi';
import Spinner from '../../components/ui/Spinner';
import OrderStatusBadge from '../../components/order/OrderStatusBadge';
import Button from '../../components/ui/Button';
import ReviewModal from '../../components/review/ReviewModal';
import StarRating from '../../components/review/StarRating';
import { formatPrice, formatDate } from '../../utils/format';
import { ROUTES } from '../../constants/routes';
import { ArrowLeft, PackageCheck, Star, Pencil, Trash2, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import ReturnRequestModal from '../../components/return/ReturnRequestModal';

const OrderDetailPage = () => {
  const { id } = useParams();
  const { data: order, isLoading, isError } = useMyOrder(id);
  const cancelOrder = useCancelOrder();
  const [cancelReason, setCancelReason] = useState('');
  const [showCancelDialog, setShowCancelDialog] = useState(false);
  const [confirmingReceived, setConfirmingReceived] = useState(false);
  const [reviewTarget, setReviewTarget] = useState(null); // { item, existingReview? }
  const [showReturnModal, setShowReturnModal] = useState(false);
  const queryClient = useQueryClient();

  const deleteMutation = useMutation({
    mutationFn: (reviewId) => reviewApi.deleteReview(reviewId),
    onSuccess: () => {
      toast.success('Đã xóa đánh giá');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi xóa đánh giá'),
  });

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError || !order) return <div className="text-center py-20 text-red-500">Lỗi tải chi tiết đơn hàng</div>;

  const address = order.addressSnapshot || {};
  const canCancel = order.status === 'PENDING';
  const canShowReviews = order.status === 'COMPLETED';

  // Return window: 7 days from deliveredAt
  const canRequestReturn = (() => {
    if (!['DELIVERED', 'COMPLETED'].includes(order.status)) return false;
    if (order.returnStatus && ['PENDING', 'APPROVED', 'RECEIVED'].includes(order.returnStatus)) return false;
    if (!order.deliveredAt) return false;
    const deadline = new Date(order.deliveredAt);
    deadline.setDate(deadline.getDate() + 7);
    return new Date() < deadline;
  })();

  const returnDaysLeft = (() => {
    if (!order.deliveredAt) return 0;
    const deadline = new Date(order.deliveredAt);
    deadline.setDate(deadline.getDate() + 7);
    const diff = Math.ceil((deadline - new Date()) / (1000 * 60 * 60 * 24));
    return Math.max(0, diff);
  })();

  const handleCancel = async () => {
    try {
      await cancelOrder.mutateAsync({ orderId: id, reason: cancelReason || undefined });
      setShowCancelDialog(false);
      window.location.reload();
    } catch (err) {
      alert(err?.response?.data?.message || 'Hủy đơn thất bại');
    }
  };

  return (
    <>
    <div className="space-y-8 max-w-4xl mx-auto">
      <div className="flex items-center gap-4">
        <Link to={ROUTES.MY_ORDERS} className="p-2 hover:bg-gray-100 rounded-full transition-colors">
          <ArrowLeft className="w-6 h-6 text-gray-600" />
        </Link>
        <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">Chi tiết đơn hàng</h1>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-gray-200 bg-gray-50 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <p className="text-sm text-gray-500">Mã đơn hàng</p>
            <p className="text-lg font-bold text-gray-900">#{order.id}</p>
          </div>
          <div className="text-left sm:text-right">
            <p className="text-sm text-gray-500 mb-1">Ngày đặt: {formatDate(order.createdAt)}</p>
            <OrderStatusBadge status={order.status} />
          </div>
        </div>

        {/* Info Grid */}
        <div className="grid sm:grid-cols-2 gap-6 p-6 border-b border-gray-200">
          <div>
            <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider mb-3">Địa chỉ nhận hàng</h3>
            <div className="text-sm text-gray-600 space-y-1">
              <p className="font-medium text-gray-900">{address.fullName}</p>
              <p>{address.phone}</p>
              <p>{address.fullAddress || `${address.street}, ${address.ward}, ${address.district}, ${address.province}`}</p>
            </div>
          </div>
          <div>
            <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider mb-3">Thanh toán</h3>
            <div className="text-sm text-gray-600 space-y-1">
              <p>Phương thức: <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${order.paymentMethod === 'VNPAY' ? 'bg-purple-100 text-purple-700' : 'bg-green-100 text-green-700'}`}>
                {order.paymentMethod === 'COD' ? 'COD — Thanh toán khi nhận hàng' : 'VNPay'}
              </span></p>
              <p>Trạng thái: <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                order.paymentStatus === 'PAID' ? 'bg-green-100 text-green-700' :
                order.paymentStatus === 'REFUNDED' ? 'bg-gray-100 text-gray-600' :
                'bg-yellow-100 text-yellow-700'
              }`}>
                {order.paymentStatus === 'PAID' ? 'Đã thanh toán' :
                 order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' :
                 'Chưa thanh toán'}
              </span></p>
              {order.paidAt && (
                <p>Thanh toán lúc: <span className="font-medium text-gray-900">{formatDate(order.paidAt)}</span></p>
              )}
              {order.expectedDeliveryDate && (
                <p>Dự kiến giao: <span className="font-medium text-gray-900">{new Date(order.expectedDeliveryDate).toLocaleDateString('vi-VN')}</span></p>
              )}
              {order.deliveredAt && (
                <p>Đã giao: <span className="font-medium text-gray-900">{formatDate(order.deliveredAt)}</span></p>
              )}
            </div>
          </div>
        </div>

        {/* Cancel reason */}
        {order.status === 'CANCELLED' && order.cancelReason && (
          <div className="p-6 border-b border-gray-200 bg-red-50">
            <h3 className="text-sm font-bold text-red-800 uppercase tracking-wider mb-2">Lý do hủy</h3>
            <p className="text-sm text-red-700">{order.cancelReason}</p>
          </div>
        )}

        {/* Note */}
        {order.note && (
          <div className="p-6 border-b border-gray-200 bg-yellow-50">
            <h3 className="text-sm font-bold text-yellow-800 uppercase tracking-wider mb-2">Ghi chú</h3>
            <p className="text-sm text-yellow-700 whitespace-pre-line">{order.note}</p>
          </div>
        )}

        {/* Return Status */}
        {order.returnStatus && (
          <div className={`p-6 border-b border-gray-200 ${
            order.returnStatus === 'REJECTED' ? 'bg-red-50' :
            order.returnStatus === 'COMPLETED' ? 'bg-green-50' :
            'bg-blue-50'
          }`}>
            <h3 className={`text-sm font-bold uppercase tracking-wider mb-2 ${
              order.returnStatus === 'REJECTED' ? 'text-red-800' :
              order.returnStatus === 'COMPLETED' ? 'text-green-800' :
              'text-blue-800'
            }`}>
              <RotateCcw className="w-4 h-4 inline mr-1.5" />
              Yêu cầu đổi/trả hoặc khiếu nại
            </h3>
            <p className={`text-sm ${
              order.returnStatus === 'REJECTED' ? 'text-red-700' :
              order.returnStatus === 'COMPLETED' ? 'text-green-700' :
              'text-blue-700'
            }`}>
              Trạng thái: <span className="font-semibold">{order.returnStatusLabel}</span>
            </p>
          </div>
        )}

        {/* Items */}
        <div className="p-6">
          <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider mb-4">Sản phẩm</h3>
          <div className="space-y-4">
            {(order.items || []).map(item => (
              <div key={item.id} className="flex gap-4">
                <img src={item.imageUrl} alt={item.productName} className="w-20 h-20 object-cover rounded-lg bg-gray-100" referrerPolicy="no-referrer" />
                <div className="grow flex flex-col justify-between">
                  <Link to={`/products/${item.productId}`} className="font-medium text-gray-900 hover:text-gray-600 line-clamp-2">
                    {item.productName}
                  </Link>
                  <p className="text-xs text-gray-500">{item.colorName} | {item.size}</p>
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-gray-500">SL: {item.quantity}</span>
                    <span className="font-bold text-gray-900">{formatPrice(item.subtotal)}</span>
                  </div>
                  {/* Review button / badge */}
                  {canShowReviews && (
                    <div className="mt-3">
                      {item.canReview && (
                        <button onClick={() => setReviewTarget({ item })}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-amber-700 bg-amber-50 border border-amber-200 rounded-lg hover:bg-amber-100 transition-colors">
                          <Star className="w-3.5 h-3.5" /> Viết đánh giá
                        </button>
                      )}
                      {item.reviewId && (
                        <div className="bg-gray-50 border border-gray-100 rounded-lg p-3">
                          <div className="flex items-center justify-between mb-1.5">
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-medium text-green-700 bg-green-100 px-2 py-0.5 rounded flex items-center gap-1">
                                ✓ Đã đánh giá
                              </span>
                              <StarRating value={item.reviewRating || 0} size={14} />
                            </div>
                            <div className="flex gap-2">
                              <button onClick={() => setReviewTarget({ 
                                item, 
                                existingReview: { id: item.reviewId, rating: item.reviewRating, comment: item.reviewComment } 
                              })}
                                className="text-gray-400 hover:text-blue-600 transition-colors" title="Sửa">
                                <Pencil className="w-3.5 h-3.5" />
                              </button>
                              <button onClick={() => {
                                if (window.confirm('Xóa đánh giá này?')) deleteMutation.mutate(item.reviewId);
                              }}
                                className="text-gray-400 hover:text-red-600 transition-colors" title="Xóa">
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </div>
                          {item.reviewComment && (
                            <p className="text-sm text-gray-700 mt-1">{item.reviewComment}</p>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Summary */}
        <div className="p-6 bg-gray-50 border-t border-gray-200">
          <div className="w-full sm:w-1/2 ml-auto space-y-3 text-sm text-gray-600">
            <div className="flex justify-between">
              <span>Tạm tính</span>
              <span className="font-medium text-gray-900">{formatPrice(order.subtotal)}</span>
            </div>
            <div className="flex justify-between">
              <span>Phí vận chuyển</span>
              <span className="font-medium text-gray-900">{formatPrice(order.shippingFee)}</span>
            </div>
            <div className="border-t border-gray-200 pt-3 flex justify-between text-lg font-bold text-gray-900">
              <span>Tổng cộng</span>
              <span className="text-red-600">{formatPrice(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Actions */}
      {canCancel && (
        <div className="flex justify-end">
          {!showCancelDialog ? (
            <Button variant="danger" onClick={() => setShowCancelDialog(true)}>Hủy đơn hàng</Button>
          ) : (
            <div className="bg-white p-6 rounded-2xl border border-red-200 shadow-sm space-y-4 w-full sm:w-96">
              <h3 className="font-bold text-gray-900">Xác nhận hủy đơn hàng?</h3>
              <textarea
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="Lý do hủy (không bắt buộc)..."
                rows={2}
                className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
              />
              <div className="flex gap-3 justify-end">
                <Button variant="secondary" size="sm" onClick={() => setShowCancelDialog(false)}>Đóng</Button>
                <Button variant="danger" size="sm" loading={cancelOrder.isPending} onClick={handleCancel}>Hủy đơn</Button>
              </div>
            </div>
          )}
        </div>
      )}

      {order.status === 'DELIVERED' && (
        <div className="space-y-3">
          {canRequestReturn && returnDaysLeft > 0 && (
            <p className="text-xs text-gray-500 text-right">
              ⏳ Còn {returnDaysLeft} ngày để tạo yêu cầu đổi/trả hoặc khiếu nại
            </p>
          )}
          <div className="flex gap-3 justify-end">
            <Button
              onClick={async () => {
                if (!window.confirm('Xác nhận bạn đã nhận được hàng?')) return;
                setConfirmingReceived(true);
                try {
                  await orderApi.confirmReceived(id);
                  window.location.reload();
                } catch (err) {
                  alert(err?.response?.data?.message || 'Lỗi xác nhận nhận hàng');
                } finally {
                  setConfirmingReceived(false);
                }
              }}
              loading={confirmingReceived}
            >
              <PackageCheck className="w-4 h-4 mr-2" />
              Đã nhận hàng
            </Button>
            {canRequestReturn && (
              <Button variant="secondary" onClick={() => setShowReturnModal(true)}>
                <RotateCcw className="w-4 h-4 mr-2" />
                Yêu cầu đổi/trả hoặc khiếu nại
              </Button>
            )}
          </div>
        </div>
      )}

      {/* Return for COMPLETED orders within window */}
      {order.status === 'COMPLETED' && canRequestReturn && (
        <div className="space-y-3">
          {returnDaysLeft > 0 && (
            <p className="text-xs text-gray-500 text-right">
              ⏳ Còn {returnDaysLeft} ngày để tạo yêu cầu đổi/trả hoặc khiếu nại
            </p>
          )}
          <div className="flex gap-3 justify-end">
            <Button variant="secondary" onClick={() => setShowReturnModal(true)}>
              <RotateCcw className="w-4 h-4 mr-2" />
              Yêu cầu đổi/trả hoặc khiếu nại
            </Button>
          </div>
        </div>
      )}
    </div>

    {/* Review Modal */}
    {reviewTarget && (
      <ReviewModal
        item={reviewTarget.item}
        existingReview={reviewTarget.existingReview}
        onClose={() => setReviewTarget(null)}
      />
    )}

    {showReturnModal && (
      <ReturnRequestModal
        orderId={order.id}
        orderItems={order.items || []}
        onClose={() => setShowReturnModal(false)}
      />
    )}
  </>
  );
};

export default OrderDetailPage;
