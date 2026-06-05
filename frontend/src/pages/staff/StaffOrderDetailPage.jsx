import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useStaffOrder } from '../../hooks/useStaffOrder';
import { useConfirmPacking } from '../../hooks/useConfirmPacking';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import { orderApi } from '../../api/orderApi';
import { shippingApi } from '../../api/shippingApi';
import { useAuth } from '../../contexts/AuthContext';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import OrderStatusTimeline from '../../components/order/OrderStatusTimeline';
import { formatPrice, formatDate, formatOrderStatus, parseReturnReason } from '../../utils/format';
import { ArrowLeft, Package, Truck, CheckCircle, XCircle, Clock, AlertTriangle, Ruler, RotateCcw } from 'lucide-react';
import { clsx } from 'clsx';
import { toast } from 'sonner';

const StaffOrderDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user: authUser } = useAuth();
  const queryClient = useQueryClient();
  const { data: order, isLoading, isError, updateStatus, isUpdating, refetch } = useStaffOrder(id);
  const confirmPacking = useConfirmPacking();

  // Cancel dialog
  const [showCancelDialog, setShowCancelDialog] = useState(false);
  const [cancelReason, setCancelReason] = useState('');

  // Packing form
  const [packing, setPacking] = useState({ packageLength: '', packageWidth: '', packageHeight: '', actualWeight: '', packingNote: '' });
  const [previewData, setPreviewData] = useState(null);
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);

  // Return actions
  const [rejectNote, setRejectNote] = useState('');
  const [showRejectDialog, setShowRejectDialog] = useState(false);
  const [completeNote, setCompleteNote] = useState('');
  const [showCompleteDialog, setShowCompleteDialog] = useState(false);

  const approveMutation = useMutation({
    mutationFn: () => (authUser?.role === 'ADMIN'
      ? returnApi.adminApprove(order?.returnId, { note: '' })
      : returnApi.approveReturn(order?.returnId, { note: '' })),
    onSuccess: () => { toast.success('Đã duyệt yêu cầu'); refetch?.(); queryClient.invalidateQueries({ queryKey: ['staffOrders'] }); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });
  const rejectMutation = useMutation({
    mutationFn: (note) => (authUser?.role === 'ADMIN'
      ? returnApi.adminReject(order?.returnId, { note })
      : returnApi.rejectReturn(order?.returnId, { note })),
    onSuccess: () => { toast.success('Đã từ chối yêu cầu'); setShowRejectDialog(false); refetch?.(); queryClient.invalidateQueries({ queryKey: ['staffOrders'] }); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });
  const receiveMutation = useMutation({
    mutationFn: () => returnApi.markReceived(order?.returnId, { note: '' }),
    onSuccess: () => { toast.success('Đã xác nhận nhận hàng'); refetch?.(); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });
  const completeMutation = useMutation({
    mutationFn: ({ note }) => returnApi.completeReturn(order?.returnId, { note: note || null }),
    onSuccess: () => { toast.success('Đã xác nhận hoàn tiền'); setShowCompleteDialog(false); setCompleteNote(''); refetch?.(); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  const confirmCompletedMutation = useMutation({
    mutationFn: () => orderApi.confirmCompleted(order?.id),
    onSuccess: () => { toast.success('Đã xác nhận hoàn thành đơn'); refetch?.(); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError || !order) return <div className="text-center py-20 text-red-500">Lỗi tải chi tiết đơn hàng</div>;

  const address = order.addressSnapshot || {};
  const feeDiffValue = order.shippingFeeDifference != null ? Number(order.shippingFeeDifference) : null;
  const feeDiffLabel = feeDiffValue == null
    ? null
    : feeDiffValue > 0
      ? `+${formatPrice(feeDiffValue)}`
      : feeDiffValue < 0
        ? `-${formatPrice(Math.abs(feeDiffValue))}`
        : formatPrice(0);
  const feeDiffClass = feeDiffValue == null
    ? 'text-gray-600'
    : feeDiffValue > 0
      ? 'text-red-600'
      : feeDiffValue < 0
        ? 'text-green-600'
        : 'text-gray-700';

  const handleUpdateStatus = async (newStatus) => {
    try {
      if (newStatus === 'CANCELLED') {
        setShowCancelDialog(true);
        return;
      }
      await updateStatus({ status: newStatus });
      refetch?.();
    } catch (error) {
      alert(error?.response?.data?.message || 'Lỗi khi cập nhật trạng thái');
    }
  };

  const handleCancel = async () => {
    if (!cancelReason.trim()) {
      alert('Vui lòng nhập lý do hủy đơn');
      return;
    }
    try {
      await updateStatus({ status: 'CANCELLED', cancelReason: cancelReason.trim() });
      setShowCancelDialog(false);
      refetch?.();
    } catch (error) {
      alert(error?.response?.data?.message || 'Lỗi khi hủy đơn');
    }
  };

  const handlePreviewFee = async () => {
    const { packageLength, packageWidth, packageHeight, actualWeight } = packing;
    const lengthNum = Number(packageLength);
    const widthNum = Number(packageWidth);
    const heightNum = Number(packageHeight);
    const weightNum = Number(actualWeight);

    if (isNaN(lengthNum) || lengthNum <= 0 ||
        isNaN(widthNum) || widthNum <= 0 ||
        isNaN(heightNum) || heightNum <= 0 ||
        isNaN(weightNum) || weightNum <= 0) {
      alert('Kích thước và cân nặng phải lớn hơn 0');
      return;
    }

    try {
      setIsPreviewLoading(true);
      const res = await shippingApi.previewActualFee(id, {
        packageLength: lengthNum,
        packageWidth: widthNum,
        packageHeight: heightNum,
        actualWeight: weightNum
      });
      setPreviewData(res.data?.data || null);
      toast.success('Đã tải phí ship thực tế từ GHN');
    } catch (error) {
      alert(error?.response?.data?.message || 'Lỗi khi xem trước phí ship');
    } finally {
      setIsPreviewLoading(false);
    }
  };

  const handleConfirmPacking = async () => {
    const { packageLength, packageWidth, packageHeight, actualWeight, packingNote } = packing;
    const lengthNum = Number(packageLength);
    const widthNum = Number(packageWidth);
    const heightNum = Number(packageHeight);
    const weightNum = Number(actualWeight);

    if (isNaN(lengthNum) || lengthNum <= 0 ||
        isNaN(widthNum) || widthNum <= 0 ||
        isNaN(heightNum) || heightNum <= 0 ||
        isNaN(weightNum) || weightNum <= 0) {
      alert('Kích thước và cân nặng phải lớn hơn 0');
      return;
    }
    try {
      await confirmPacking.mutateAsync({
        orderId: id,
        data: {
          packageLength: lengthNum,
          packageWidth: widthNum,
          packageHeight: heightNum,
          actualWeight: weightNum,
          packingNote: packingNote || null,
        },
      });
      refetch?.();
    } catch (error) {
      alert(error?.response?.data?.message || 'Lỗi khi xác nhận đóng gói');
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'PENDING': case 'AWAITING_PAYMENT': return <Clock className="w-5 h-5" />;
      case 'CONFIRMED': return <Package className="w-5 h-5" />;
      case 'SHIPPING':  return <Truck className="w-5 h-5" />;
      case 'DELIVERED': case 'COMPLETED': return <CheckCircle className="w-5 h-5" />;
      case 'CANCELLED': return <XCircle className="w-5 h-5" />;
      case 'RETURN_REQUESTED': case 'RETURNING': case 'RETURNED': return <RotateCcw className="w-5 h-5" />;
      default: return <Clock className="w-5 h-5" />;
    }
  };

  const statusColor = (s) => {
    if (s === 'PENDING' || s === 'AWAITING_PAYMENT') return 'bg-yellow-100 text-yellow-800';
    if (s === 'CONFIRMED') return 'bg-blue-100 text-blue-800';
    if (s === 'SHIPPING') return 'bg-indigo-100 text-indigo-800';
    if (s === 'DELIVERED' || s === 'COMPLETED') return 'bg-green-100 text-green-800';
    if (s === 'RETURN_REQUESTED' || s === 'RETURNING') return 'bg-orange-100 text-orange-800';
    if (s === 'RETURNED') return 'bg-purple-100 text-purple-800';
    return 'bg-red-100 text-red-800';
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex items-center gap-4">
        <Button variant="ghost" className="p-2" onClick={() => navigate('/staff/orders')}>
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng #{order.id}</h1>
        <div className="ml-auto">
          <span className={clsx('px-3 py-1 rounded-full text-sm font-medium flex items-center gap-2', statusColor(order.status))}>
            {getStatusIcon(order.status)}
            {formatOrderStatus(order.status).label}
          </span>
        </div>
      </div>

      {/* Order Status Timeline */}
      <OrderStatusTimeline status={order.status} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Items */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="p-4 border-b border-gray-200 bg-gray-50">
              <h2 className="font-semibold text-gray-900">Sản phẩm ({(order.items || []).length})</h2>
            </div>
            <div className="divide-y divide-gray-100">
              {(order.items || []).map((item) => (
                <div key={item.id} className="p-4 flex gap-4">
                  <img src={item.imageUrl} alt={item.productName} className="w-20 h-20 object-cover rounded-lg bg-gray-100" referrerPolicy="no-referrer" />
                  <div className="flex-1">
                    <h3 className="font-medium text-gray-900">{item.productName}</h3>
                    <p className="text-sm text-gray-500 mt-1">Phân loại: {item.colorName}, Size {item.size}</p>
                    <div className="flex justify-between items-center mt-2">
                      <span className="text-sm text-gray-600">SL: {item.quantity}</span>
                      <span className="font-medium text-gray-900">{formatPrice(item.subtotal)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-4 bg-gray-50 border-t border-gray-200">
              <div className="space-y-2 text-sm text-gray-600">
                <div className="flex justify-between">
                  <span>Tạm tính</span>
                  <span className="font-medium text-gray-900">{formatPrice(order.subtotal)}</span>
                </div>
                <div className="flex justify-between">
                  <span>Phí ship</span>
                  <span className="font-medium text-gray-900">{formatPrice(order.shippingFee)}</span>
                </div>
                <div className="flex justify-between text-base font-bold text-gray-900 pt-2 border-t border-gray-200">
                  <span>Tổng cộng</span>
                  <span className="text-red-600">{formatPrice(order.totalAmount)}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Packing Section — chỉ hiện khi CONFIRMED */}
          {order.status === 'CONFIRMED' && !order.packingConfirmed && (
            <div className="bg-white rounded-xl shadow-sm border border-blue-200 p-6">
              <h2 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
                <Ruler className="w-5 h-5 text-blue-600" />
                Đóng gói kiện hàng
              </h2>
              <p className="text-sm text-gray-500 mb-4">
                Bắt buộc khai báo kích thước và cân nặng trước khi chuyển sang giao hàng.
              </p>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-4">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Dài (cm)</label>
                  <input type="number" min="1" max="200" value={packing.packageLength} onChange={e => setPacking(p => ({...p, packageLength: e.target.value}))}
                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-blue-500 focus:border-blue-500" placeholder="cm" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Rộng (cm)</label>
                  <input type="number" min="1" max="200" value={packing.packageWidth} onChange={e => setPacking(p => ({...p, packageWidth: e.target.value}))}
                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-blue-500 focus:border-blue-500" placeholder="cm" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Cao (cm)</label>
                  <input type="number" min="1" max="200" value={packing.packageHeight} onChange={e => setPacking(p => ({...p, packageHeight: e.target.value}))}
                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-blue-500 focus:border-blue-500" placeholder="cm" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Cân nặng (g)</label>
                  <input type="number" min="1" max="50000" value={packing.actualWeight} onChange={e => setPacking(p => ({...p, actualWeight: e.target.value}))}
                    className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-blue-500 focus:border-blue-500" placeholder="gram" />
                </div>
              </div>
              <div className="mb-4">
                <label className="block text-xs text-gray-500 mb-1">Ghi chú đóng gói (không bắt buộc)</label>
                <input type="text" value={packing.packingNote} onChange={e => setPacking(p => ({...p, packingNote: e.target.value}))}
                  className="w-full border border-gray-300 rounded-lg p-2 text-sm focus:ring-blue-500 focus:border-blue-500" placeholder="VD: Bọc thêm bubble wrap..." />
              </div>

              <div className="flex flex-wrap gap-3 mb-4">
                <Button type="button" onClick={handlePreviewFee} loading={isPreviewLoading} variant="outline" className="text-blue-600 border-blue-200 hover:bg-blue-50">
                  Tính thử phí ship GHN
                </Button>
              </div>

              {previewData && (
                <div className="mb-4 p-4 bg-blue-50 border border-blue-200 rounded-lg space-y-2 text-sm text-gray-700">
                  <div className="flex justify-between">
                    <span>Phí ship khách đã trả:</span>
                    <span className="font-semibold text-gray-900">{formatPrice(previewData.customerShippingFee)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Phí ship thực tế (GHN):</span>
                    <span className="font-semibold text-gray-900">{formatPrice(previewData.actualGhnFee)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Chênh lệch:</span>
                    <span className={`font-bold ${
                      Number(previewData.difference) > 0 ? 'text-red-600' :
                      Number(previewData.difference) < 0 ? 'text-green-600' : 'text-gray-700'
                    }`}>
                      {Number(previewData.difference) > 0 ? `+${formatPrice(previewData.difference)}` :
                       Number(previewData.difference) < 0 ? `-${formatPrice(Math.abs(previewData.difference))}` :
                       formatPrice(0)}
                    </span>
                  </div>
                </div>
              )}

              <Button onClick={handleConfirmPacking} loading={confirmPacking.isPending} className="bg-blue-600 hover:bg-blue-700">
                <Package className="w-4 h-4 mr-2" /> Xác nhận đóng gói
              </Button>
            </div>
          )}

          {/* Packing confirmed info */}
          {order.packingConfirmed && (
            <div className="bg-white rounded-xl shadow-sm border border-green-200 p-6">
              <h2 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                <CheckCircle className="w-5 h-5 text-green-600" />
                Đã đóng gói
              </h2>
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 text-sm">
                <div><span className="text-gray-500 block">Kích thước</span><span className="font-medium">{order.packageLength}×{order.packageWidth}×{order.packageHeight} cm</span></div>
                <div><span className="text-gray-500 block">Cân nặng</span><span className="font-medium">{order.actualWeight}g</span></div>
                {order.packingNote && (
                  <div><span className="text-gray-500 block">Ghi chú đóng gói</span><span className="font-medium">{order.packingNote}</span></div>
                )}
              </div>

              {order.estimatedShippingFee != null && (
                <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-4 text-sm">
                  <div>
                    <span className="text-gray-500 block">Phí ship khách đã trả</span>
                    <span className="font-medium">{formatPrice(order.shippingFee)}</span>
                  </div>
                  <div>
                    <span className="text-gray-500 block">Phí ship ước tính</span>
                    <span className="font-medium">{formatPrice(order.estimatedShippingFee)}</span>
                  </div>
                  <div>
                    <span className="text-gray-500 block">Chênh lệch</span>
                    <span className={`font-semibold ${feeDiffClass}`}>{feeDiffLabel}</span>
                  </div>
                </div>
              )}

              {order.paymentMethod === 'VNPAY' && (
                <p className="text-xs text-gray-500 mt-3">
                  Đơn thanh toán online. Phí ship ước tính chỉ dùng để kiểm tra nội bộ, không tự động thu thêm khách.
                </p>
              )}
              {order.paymentMethod === 'COD' && (
                <p className="text-xs text-gray-500 mt-3">
                  Phí ship ước tính dùng để staff tham khảo khi giao COD.
                </p>
              )}
              {/* Warnings */}
              {order.packingWarnings?.length > 0 && (
                <div className="mt-3 space-y-1">
                  {order.packingWarnings.map((w, i) => (
                    <div key={i} className="flex items-start gap-2 text-sm text-amber-700 bg-amber-50 p-2 rounded-lg">
                      <AlertTriangle className="w-4 h-4 mt-0.5 shrink-0" />
                      <span>{w}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Status Update Actions */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Cập nhật trạng thái</h2>
            {order.status === 'CANCELLED' || order.status === 'COMPLETED' || order.status === 'RETURNED' ? (
              <p className="text-gray-500">Đơn hàng đã kết thúc, không thể cập nhật trạng thái.</p>
            ) : order.status === 'RETURN_REQUESTED' || order.status === 'RETURNING' ? (
              <div className="space-y-3">
                <p className="text-orange-600 font-medium flex items-center gap-2">
                  <AlertTriangle className="w-5 h-5" /> Đơn hàng đang trong quá trình xử lý Trả hàng.
                </p>
              </div>
            ) : (
              <div className="flex flex-wrap gap-3">
                {order.status === 'AWAITING_PAYMENT' && (
                  <Button variant="outline" onClick={() => handleUpdateStatus('CANCELLED')} className="text-red-600 border-red-200 hover:bg-red-50">
                    Hủy đơn
                  </Button>
                )}
                {order.status === 'PENDING' && (
                  <>
                    <Button onClick={() => handleUpdateStatus('CONFIRMED')} loading={isUpdating} className="bg-blue-600 hover:bg-blue-700">
                      Xác nhận đơn hàng
                    </Button>
                    <Button variant="outline" onClick={() => handleUpdateStatus('CANCELLED')} className="text-red-600 border-red-200 hover:bg-red-50">
                      Hủy đơn
                    </Button>
                  </>
                )}
                {order.status === 'CONFIRMED' && (
                  <>
                    <Button
                      onClick={() => handleUpdateStatus('SHIPPING')}
                      loading={isUpdating}
                      disabled={!order.packingConfirmed}
                      className="bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50"
                    >
                      <Truck className="w-4 h-4 mr-2" />
                      {order.packingConfirmed ? 'Bắt đầu giao hàng' : 'Cần đóng gói trước'}
                    </Button>
                    <Button variant="outline" onClick={() => handleUpdateStatus('CANCELLED')} className="text-red-600 border-red-200 hover:bg-red-50">
                      Hủy đơn
                    </Button>
                  </>
                )}
                {(order.status === 'SHIPPING' || order.status === 'DELIVERED') && (
                  <Button
                    onClick={() => confirmCompletedMutation.mutate()}
                    loading={confirmCompletedMutation.isPending}
                    className="bg-green-600 hover:bg-green-700"
                  >
                    <CheckCircle className="w-4 h-4 mr-2" /> Xác nhận hoàn thành
                  </Button>
                )}
              </div>
            )}
          </div>

          {/* Cancel dialog */}
          {showCancelDialog && (
            <div className="bg-white rounded-xl shadow-sm border border-red-200 p-6 space-y-4">
              <h3 className="font-bold text-red-800">Hủy đơn hàng #{order.id}</h3>
              <p className="text-sm text-gray-600">Staff/Admin bắt buộc phải nhập lý do hủy đơn.</p>
              <textarea
                value={cancelReason}
                onChange={(e) => setCancelReason(e.target.value)}
                placeholder="Lý do hủy đơn (bắt buộc)..."
                rows={3}
                className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-red-500 focus:border-red-500 resize-none"
              />
              <div className="flex gap-3 justify-end">
                <Button variant="secondary" size="sm" onClick={() => setShowCancelDialog(false)}>Đóng</Button>
                <Button variant="danger" size="sm" loading={isUpdating} onClick={handleCancel} disabled={!cancelReason.trim()}>
                  Xác nhận hủy
                </Button>
              </div>
            </div>
          )}
          {/* Return Info Card */}
          {order.returnId && (
            <div className="bg-orange-50 rounded-xl shadow-sm border border-orange-200 p-6 space-y-4">
              <h2 className="font-semibold text-orange-800 flex items-center gap-2">
                <RotateCcw className="w-5 h-5" /> Yêu cầu trả hàng #{order.returnId}
              </h2>
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div><span className="text-gray-500">Trạng thái:</span> <span className="font-semibold">{order.returnStatusLabel || order.returnStatus}</span></div>
                {order.returnRefundAmount && (
                  <div><span className="text-gray-500">Số tiền hoàn:</span> <span className="font-medium text-green-600">{formatPrice(order.returnRefundAmount)}</span></div>
                )}
              </div>
              {order.returnReason && (
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="text-sm font-bold text-gray-900">Nội dung yêu cầu</h3>
                    <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-blue-50 text-blue-700">
                      {parseReturnReason(order.returnReason).typeLabel}
                    </span>
                  </div>
                  <p className="text-sm text-gray-700 bg-white rounded-lg p-3 whitespace-pre-line">
                    {parseReturnReason(order.returnReason).cleanReason}
                  </p>
                </div>
              )}
              {order.returnAdminNote && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-1">Ghi chú xử lý</h3>
                  <p className="text-sm text-gray-700 bg-yellow-100 rounded-lg p-3 whitespace-pre-line">{order.returnAdminNote}</p>
                </div>
              )}
              {order.returnEvidenceImages?.length > 0 && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-2">Ảnh minh chứng</h3>
                  <div className="grid grid-cols-4 gap-2">
                    {order.returnEvidenceImages.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer" className="aspect-square rounded-lg overflow-hidden border border-gray-200">
                        <img src={url} alt="" className="w-full h-full object-cover" referrerPolicy="no-referrer" />
                      </a>
                    ))}
                  </div>
                </div>
              )}
              {/* Return Actions */}
              <div className="flex flex-wrap gap-2 pt-2 border-t border-orange-200">
                {order.returnStatus === 'REQUESTED' && (
                  <>
                    <Button onClick={() => { if (window.confirm('Duyệt yêu cầu này?')) approveMutation.mutate(); }} loading={approveMutation.isPending} className="bg-blue-600 hover:bg-blue-700">
                      <CheckCircle className="w-4 h-4 mr-1" /> Duyệt
                    </Button>
                    <Button variant="danger" onClick={() => setShowRejectDialog(true)}>
                      <XCircle className="w-4 h-4 mr-1" /> Từ chối
                    </Button>
                  </>
                )}
                {order.returnStatus === 'APPROVED' && authUser?.role === 'ADMIN' && (
                  <Button onClick={() => { if (window.confirm('Xác nhận đã nhận hàng/ghi nhận xử lý?')) receiveMutation.mutate(); }} loading={receiveMutation.isPending} className="bg-purple-600 hover:bg-purple-700">
                    <Package className="w-4 h-4 mr-1" /> Xác nhận xử lý
                  </Button>
                )}
                {order.returnStatus === 'RECEIVED' && authUser?.role === 'ADMIN' && (
                  <Button onClick={() => setShowCompleteDialog(true)} className="bg-green-600 hover:bg-green-700">
                    <CheckCircle className="w-4 h-4 mr-1" /> Xác nhận hoàn tiền
                  </Button>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Customer Info */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Thông tin khách hàng</h2>
            <div className="space-y-3 text-sm">
              <div>
                <span className="text-gray-500 block mb-1">Họ tên</span>
                <span className="font-medium text-gray-900">{address.fullName}</span>
              </div>
              <div>
                <span className="text-gray-500 block mb-1">Số điện thoại</span>
                <span className="font-medium text-gray-900">{address.phone}</span>
              </div>
              <div>
                <span className="text-gray-500 block mb-1">Địa chỉ giao hàng</span>
                <span className="font-medium text-gray-900 leading-relaxed">
                  {address.fullAddress || `${address.street}, ${address.ward}, ${address.district}, ${address.province}`}
                </span>
              </div>
            </div>
          </div>

          {/* Payment Info */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Thanh toán</h2>
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Phương thức</span>
                <span className="font-medium text-gray-900">{order.paymentMethod === 'COD' ? 'COD' : 'VNPAY'}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-gray-500">Trạng thái TT</span>
                <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${
                  order.paymentStatus === 'PAID' ? 'bg-green-100 text-green-700' :
                  order.paymentStatus === 'REFUNDED' ? 'bg-gray-100 text-gray-600' :
                  'bg-yellow-100 text-yellow-700'
                }`}>
                  {order.paymentStatus === 'PAID' ? 'Đã thanh toán' :
                   order.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền' :
                   'Chưa thanh toán'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Ngày đặt</span>
                <span className="font-medium text-gray-900">{formatDate(order.createdAt)}</span>
              </div>
              {order.expectedDeliveryDate && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Dự kiến giao</span>
                  <span className="font-medium text-gray-900">{new Date(order.expectedDeliveryDate).toLocaleDateString('vi-VN')}</span>
                </div>
              )}
              {order.deliveredAt && (
                <div className="flex justify-between">
                  <span className="text-gray-500">Đã giao</span>
                  <span className="font-medium text-green-700">{formatDate(order.deliveredAt)}</span>
                </div>
              )}
            </div>
          </div>

          {/* Cancel reason if cancelled */}
          {order.status === 'CANCELLED' && order.cancelReason && (
            <div className="bg-red-50 rounded-xl shadow-sm border border-red-200 p-6">
              <h2 className="font-semibold text-red-800 mb-2">Lý do hủy</h2>
              <p className="text-sm text-red-700">{order.cancelReason}</p>
            </div>
          )}

          {/* Note */}
          {order.note && (
            <div className="bg-yellow-50 rounded-xl shadow-sm border border-yellow-200 p-6">
              <h2 className="font-semibold text-yellow-800 mb-2">Ghi chú</h2>
              <p className="text-sm text-yellow-700 whitespace-pre-line">{order.note}</p>
            </div>
          )}
        </div>
      </div>

      {/* Reject Return Dialog */}
      {showRejectDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Từ chối yêu cầu</h3>
            <textarea
              value={rejectNote}
              onChange={(e) => setRejectNote(e.target.value)}
              placeholder="Lý do từ chối (bắt buộc)..."
              rows={3}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <div className="flex gap-3 justify-end">
              <Button variant="secondary" onClick={() => { setShowRejectDialog(false); setRejectNote(''); }}>Hủy</Button>
              <Button variant="danger" loading={rejectMutation.isPending} onClick={() => {
                if (!rejectNote.trim()) { toast.error('Vui lòng nhập lý do'); return; }
                rejectMutation.mutate(rejectNote.trim());
              }}>Từ chối</Button>
            </div>
          </div>
        </div>
      )}

      {/* Complete Return Dialog */}
      {showCompleteDialog && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Xác nhận hoàn tiền</h3>
            <textarea
              value={completeNote}
              onChange={(e) => setCompleteNote(e.target.value)}
              placeholder="Ghi chú (không bắt buộc)..."
              rows={3}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <div className="flex gap-3 justify-end">
              <Button variant="secondary" onClick={() => { setShowCompleteDialog(false); setCompleteNote(''); }}>Hủy</Button>
              <Button loading={completeMutation.isPending} onClick={() => {
                if (!window.confirm('Xác nhận đã xử lý yêu cầu này?')) return;
                completeMutation.mutate({
                  note: completeNote.trim() || null,
                });
              }}>Xác nhận hoàn tiền</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StaffOrderDetailPage;
