import { useParams, Link } from 'react-router-dom';
import { useMyOrder } from '../../hooks/useMyOrder';
import Spinner from '../../components/ui/Spinner';
import OrderStatusBadge from '../../components/order/OrderStatusBadge';
import Button from '../../components/ui/Button';
import { formatPrice, formatDate } from '../../utils/format';
import { ROUTES } from '../../constants/routes';
import { ArrowLeft } from 'lucide-react';

const OrderDetailPage = () => {
  const { id } = useParams();
  const { data, isLoading, isError } = useMyOrder(id);

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải chi tiết đơn hàng</div>;

  // Fallback mock data
  const order = data || {
    id,
    createdAt: new Date().toISOString(),
    status: 'SHIPPING',
    paymentMethod: 'COD',
    shippingAddress: {
      fullName: 'Nguyễn Văn A',
      phone: '0912345678',
      address: '123 Đường ABC, Phường XYZ, Quận 1, TP.HCM'
    },
    items: [
      { id: 1, productId: 1, name: 'Áo Thun Basic Nam', price: 250000, quantity: 2, imageUrl: 'https://picsum.photos/seed/1/100/100' },
      { id: 2, productId: 3, name: 'Váy Hoa Mùa Hè', price: 450000, quantity: 1, imageUrl: 'https://picsum.photos/seed/3/100/100' },
    ],
    subtotal: 950000,
    shippingFee: 30000,
    totalAmount: 980000
  };

  return (
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
              <p className="font-medium text-gray-900">{order.shippingAddress.fullName}</p>
              <p>{order.shippingAddress.phone}</p>
              <p>{order.shippingAddress.address}</p>
            </div>
          </div>
          <div>
            <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider mb-3">Thanh toán</h3>
            <div className="text-sm text-gray-600 space-y-1">
              <p>Phương thức: <span className="font-medium text-gray-900">{order.paymentMethod}</span></p>
              <p>Trạng thái: <span className="font-medium text-green-600">Đã thanh toán</span></p>
            </div>
          </div>
        </div>

        {/* Items */}
        <div className="p-6">
          <h3 className="text-sm font-bold text-gray-900 uppercase tracking-wider mb-4">Sản phẩm</h3>
          <div className="space-y-4">
            {order.items.map(item => (
              <div key={item.id} className="flex gap-4">
                <img src={item.imageUrl} alt={item.name} className="w-20 h-20 object-cover rounded-lg bg-gray-100" referrerPolicy="no-referrer" />
                <div className="flex-grow flex flex-col justify-between">
                  <Link to={`/products/${item.productId}`} className="font-medium text-gray-900 hover:text-gray-600 line-clamp-2">
                    {item.name}
                  </Link>
                  <div className="flex justify-between items-center text-sm">
                    <span className="text-gray-500">Số lượng: {item.quantity}</span>
                    <span className="font-bold text-gray-900">{formatPrice(item.price * item.quantity)}</span>
                  </div>
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
      {order.status === 'PENDING' && (
        <div className="flex justify-end">
          <Button variant="danger">Hủy đơn hàng</Button>
        </div>
      )}
      {order.status === 'DELIVERED' && (
        <div className="flex justify-end">
          <Button variant="secondary">Yêu cầu đổi/trả</Button>
        </div>
      )}
    </div>
  );
};

export default OrderDetailPage;
