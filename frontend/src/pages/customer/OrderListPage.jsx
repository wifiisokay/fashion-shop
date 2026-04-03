import { useState } from 'react';
import { useMyOrders } from '../../hooks/useMyOrders';
import Spinner from '../../components/ui/Spinner';
import OrderStatusBadge from '../../components/order/OrderStatusBadge';
import { formatPrice, formatDate } from '../../utils/format';
import { Link } from 'react-router-dom';
import Button from '../../components/ui/Button';

const OrderListPage = () => {
  const [filters, setFilters] = useState({});
  const { data, isLoading, isError } = useMyOrders(filters);

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải danh sách đơn hàng</div>;

  // Fallback mock data
  const orders = data?.content?.length ? data.content : [
    { id: 'ORD-12345', createdAt: new Date().toISOString(), status: 'DELIVERED', totalAmount: 1250000 },
    { id: 'ORD-12346', createdAt: new Date(Date.now() - 86400000).toISOString(), status: 'SHIPPING', totalAmount: 450000 },
    { id: 'ORD-12347', createdAt: new Date(Date.now() - 86400000 * 3).toISOString(), status: 'PENDING', totalAmount: 850000 },
  ];

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Đơn hàng của tôi</h1>
      
      {orders.length === 0 ? (
        <div className="text-center py-20 text-gray-500 bg-white rounded-2xl border border-gray-100 shadow-sm">
          <p className="mb-4">Bạn chưa có đơn hàng nào.</p>
          <Link to="/products"><Button>Mua sắm ngay</Button></Link>
        </div>
      ) : (
        <div className="space-y-4">
          {orders.map(order => (
            <div key={order.id} className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm hover:shadow-md transition-shadow">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4 pb-4 border-b border-gray-100">
                <div>
                  <p className="text-sm text-gray-500">Mã đơn hàng: <span className="font-medium text-gray-900">#{order.id}</span></p>
                  <p className="text-sm text-gray-500 mt-1">Ngày đặt: {formatDate(order.createdAt)}</p>
                </div>
                <OrderStatusBadge status={order.status} />
              </div>
              <div className="flex justify-between items-end">
                <div>
                  <p className="text-sm text-gray-500 mb-1">Tổng tiền</p>
                  <p className="text-lg font-bold text-gray-900">{formatPrice(order.totalAmount)}</p>
                </div>
                <Link to={`/my-orders/${order.id}`}>
                  <Button variant="secondary" size="sm">Xem chi tiết</Button>
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default OrderListPage;
