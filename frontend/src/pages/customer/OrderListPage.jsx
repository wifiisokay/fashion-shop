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
  { key: 'COMPLETED', label: 'Hoàn thành' },
  { key: 'CANCELLED', label: 'Đã hủy' },
];

const OrderListPage = () => {
  const [activeStatus, setActiveStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(0);

  const params = {
    ...(activeStatus && { status: activeStatus }),
    ...(keyword && { keyword }),
    page,
    size: 5
  };

  const { data, isLoading, isError } = useMyOrders(params);

  const handleSearch = (e) => {
    e.preventDefault();
    setKeyword(searchInput);
    setPage(0);
  };

  const handleStatusChange = (status) => {
    setActiveStatus(status);
    setPage(0);
  };

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải danh sách đơn hàng</div>;

  const orders = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl sm:text-3xl font-bold text-gray-900">Đơn hàng của tôi</h1>
        <form onSubmit={handleSearch} className="relative w-full sm:w-72">
          <input
            type="text"
            placeholder="Tìm theo mã đơn, tên sản phẩm..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            className="w-full pl-4 pr-10 py-2 text-sm border border-gray-300 rounded-full focus:outline-none focus:ring-2 focus:ring-primary/50"
          />
          <button type="submit" className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-primary">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
          </button>
        </form>
      </div>

      {/* Status tabs */}
      <div className="flex overflow-x-auto pb-2 gap-2 hide-scrollbar border-b border-gray-100">
        {STATUS_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => handleStatusChange(tab.key)}
            className={clsx(
              'whitespace-nowrap px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              activeStatus === tab.key
                ? 'border-primary text-primary'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>
      
      {orders.length === 0 ? (
        <div className="text-center py-20 text-gray-500 bg-white rounded-2xl border border-gray-100 shadow-sm">
          <p className="mb-4">Không tìm thấy đơn hàng nào.</p>
          {!keyword && !activeStatus && <Link to={ROUTES.PRODUCTS}><Button>Mua sắm ngay</Button></Link>}
        </div>
      ) : (
        <div className="space-y-4">
          {orders.map(order => (
            <div key={order.id} className="bg-white rounded-2xl border border-gray-100 p-5 sm:p-6 shadow-sm hover:shadow-md transition-shadow">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-4 border-b border-gray-100">
                <div>
                  <p className="text-sm text-gray-500">Mã đơn hàng: <span className="font-medium text-gray-900">#{order.id}</span></p>
                  <p className="text-xs text-gray-400 mt-1">{formatDate(order.createdAt)}</p>
                </div>
                <OrderStatusBadge status={order.status} />
              </div>
              <div className="flex justify-between items-center mt-4">
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">Tổng tiền</p>
                  <p className="text-lg font-bold text-gray-900">{formatPrice(order.totalAmount)}</p>
                </div>
                <Link to={`${ROUTES.MY_ORDERS}/${order.id}`}>
                  <Button variant="outline" size="sm">Xem chi tiết</Button>
                </Link>
              </div>
            </div>
          ))}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-8">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1 border rounded disabled:opacity-50 hover:bg-gray-50"
              >
                Trước
              </button>
              <span className="text-sm text-gray-600">Trang {page + 1} / {totalPages}</span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1 border rounded disabled:opacity-50 hover:bg-gray-50"
              >
                Sau
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default OrderListPage;
