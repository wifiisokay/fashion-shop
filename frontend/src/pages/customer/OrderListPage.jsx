import { useState } from 'react';
import { useMyOrders } from '../../hooks/useMyOrders';
import Spinner from '../../components/ui/Spinner';
import OrderStatusBadge from '../../components/order/OrderStatusBadge';
import { formatPrice, formatDate } from '../../utils/format';
import { Link } from 'react-router-dom';
import Button from '../../components/ui/Button';
import { ROUTES } from '../../constants/routes';
import { clsx } from 'clsx';

const STATUS_TABS = [
  { key: '', label: 'Tất cả' },
  { key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' },
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'CONFIRMED', label: 'Đã xác nhận' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];

const OrderListPage = () => {
  const [activeStatus, setActiveStatus] = useState('');
  const params = activeStatus ? { status: activeStatus } : {};
  const { data, isLoading, isError } = useMyOrders(params);

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải danh sách đơn hàng</div>;

  const orders = data?.content || [];

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Đơn hàng của tôi</h1>

      {/* Status tabs */}
      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => setActiveStatus(tab.key)}
            className={clsx(
              'px-4 py-2 text-sm font-medium rounded-full border transition-colors',
              activeStatus === tab.key
                ? 'bg-black text-white border-black'
                : 'bg-white text-gray-600 border-gray-200 hover:bg-gray-50'
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>
      
      {orders.length === 0 ? (
        <div className="text-center py-20 text-gray-500 bg-white rounded-2xl border border-gray-100 shadow-sm">
          <p className="mb-4">Bạn chưa có đơn hàng nào.</p>
          <Link to={ROUTES.PRODUCTS}><Button>Mua sắm ngay</Button></Link>
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
                <Link to={`${ROUTES.MY_ORDERS}/${order.id}`}>
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
