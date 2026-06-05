import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { orderApi } from '../../api/orderApi';
import { useCategories } from '../../hooks/useCategories';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, formatDate, formatOrderStatus } from '../../utils/format';
import { clsx } from 'clsx';
import { Eye, Search, ShoppingBag, Clock, Truck, CheckCircle, RotateCcw, XCircle } from 'lucide-react';

const STATUS_COLOR_MAP = {
  gold:    'bg-yellow-100 text-yellow-800',
  blue:    'bg-blue-100 text-blue-800',
  cyan:    'bg-cyan-100 text-cyan-800',
  green:   'bg-green-100 text-green-800',
  red:     'bg-red-100 text-red-800',
  orange:  'bg-orange-100 text-orange-800',
  purple:  'bg-purple-100 text-purple-800',
  default: 'bg-gray-100 text-gray-800',
};

const StaffOrderListPage = () => {
  const [keyword, setKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [categoryFilter, setCategoryFilter] = useState('');
  const [page, setPage] = useState(0);

  const { data: categories } = useCategories();

  // Stats
  const { data: stats } = useQuery({
    queryKey: ['orderStats'],
    queryFn: () => orderApi.getOrderStats().then(r => r.data?.data),
  });

  // Orders
  const { data, isLoading } = useQuery({
    queryKey: ['staffOrders', page, keyword, statusFilter, categoryFilter],
    queryFn: () => orderApi.getAllOrders({
      page,
      size: 15,
      status: statusFilter || undefined,
      keyword: keyword || undefined,
      categoryId: categoryFilter || undefined,
    }).then(r => r.data?.data),
    keepPreviousData: true,
  });

  const orders = data?.content || [];

  const columns = [
    { title: 'Mã đơn', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium">#{val}</span> },
    {
      title: 'Khách hàng',
      dataIndex: 'customerName',
      key: 'customerName',
      render: (val) => <span className="text-sm text-gray-700">{val || '—'}</span>
    },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', key: 'totalAmount', render: (val) => <span className="font-medium text-gray-900">{formatPrice(val)}</span> },
    {
      title: 'Thanh toán',
      dataIndex: 'paymentMethod',
      key: 'paymentMethod',
      render: (val, row) => (
        <div className="space-y-1">
          <div>{val === 'COD' ? 'COD' : 'VNPAY'}</div>
          <span className={clsx(
            'inline-flex rounded-full px-2 py-0.5 text-xs font-medium',
            row.paymentStatus === 'PAID' ? 'bg-green-100 text-green-700'
              : row.paymentStatus === 'REFUNDED' ? 'bg-gray-100 text-gray-600'
              : 'bg-yellow-100 text-yellow-700'
          )}>
            {row.paymentStatus === 'PAID' ? 'Đã thanh toán'
              : row.paymentStatus === 'REFUNDED' ? 'Đã hoàn tiền'
              : 'Chưa thanh toán'}
          </span>
        </div>
      )
    },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (val) => {
        const { label, color } = formatOrderStatus(val);
        return <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', STATUS_COLOR_MAP[color] || STATUS_COLOR_MAP.default)}>{label}</span>;
      }
    },
    { title: 'Ngày tạo', dataIndex: 'createdAt', key: 'createdAt', render: (val) => formatDate(val) },
    { 
      title: 'Hành động', 
      dataIndex: 'id', 
      key: 'action',
      render: (id) => (
        <Link to={`/staff/orders/${id}`}>
          <Button variant="ghost" size="sm" className="text-blue-600 hover:text-blue-800 hover:bg-blue-50">
            <Eye className="w-4 h-4 mr-1" /> Xem
          </Button>
        </Link>
      )
    },
  ];

  // Build flat category options for select
  const buildCategoryOptions = (cats, depth = 0) => {
    let options = [];
    cats?.forEach(c => {
      options.push({ value: c.id, label: `${'— '.repeat(depth)}${c.name}` });
      if (c.children?.length) {
        options = options.concat(buildCategoryOptions(c.children, depth + 1));
      }
    });
    return options;
  };
  const categoryOptions = buildCategoryOptions(categories);

  const statCards = [
    { label: 'Tổng đơn', value: stats?.totalOrders || 0, icon: ShoppingBag, color: 'bg-indigo-50 text-indigo-600' },
    { label: 'Chờ xác nhận', value: stats?.pendingCount || 0, icon: Clock, color: 'bg-yellow-50 text-yellow-600' },
    { label: 'Đang giao', value: stats?.shippingCount || 0, icon: Truck, color: 'bg-cyan-50 text-cyan-600' },
    { label: 'Hoàn thành', value: stats?.completedCount || 0, icon: CheckCircle, color: 'bg-green-50 text-green-600' },
    { label: 'Trả hàng', value: stats?.returnCount || 0, icon: RotateCcw, color: 'bg-orange-50 text-orange-600' },
    { label: 'Đã hủy', value: stats?.cancelledCount || 0, icon: XCircle, color: 'bg-red-50 text-red-600' },
  ];

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-gray-900">Quản lý Đơn hàng</h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {statCards.map(card => {
          const Icon = card.icon;
          return (
            <div key={card.label} className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-xs font-medium text-gray-500">{card.label}</p>
                  <h3 className="text-xl font-bold text-gray-900 mt-1">{card.value}</h3>
                </div>
                <div className={clsx('w-9 h-9 rounded-full flex items-center justify-center', card.color)}>
                  <Icon className="w-4 h-4" />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-4 border-b border-gray-200 flex flex-col sm:flex-row gap-3">
          <form onSubmit={(e) => { e.preventDefault(); setKeyword(searchInput); setPage(0); }} className="relative flex-1 max-w-sm">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-4 w-4 text-gray-400" />
            </div>
            <input
              type="text"
              placeholder="Mã đơn, tên sản phẩm, khách hàng..."
              value={searchInput}
              onChange={(e) => setSearchInput(e.target.value)}
              className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-black focus:border-black text-sm"
            />
          </form>
          <select
            value={statusFilter}
            onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black py-2 pl-3 pr-10 border"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="AWAITING_PAYMENT">Chờ thanh toán</option>
            <option value="PENDING">Chờ xác nhận</option>
            <option value="CONFIRMED">Đã xác nhận</option>
            <option value="SHIPPING">Đang giao</option>
            <option value="COMPLETED">Hoàn thành</option>
            <option value="CANCELLED">Đã hủy</option>
          </select>
          <select
            value={categoryFilter}
            onChange={(e) => { setCategoryFilter(e.target.value); setPage(0); }}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black py-2 pl-3 pr-10 border"
          >
            <option value="">Tất cả danh mục</option>
            {categoryOptions.map(opt => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <div className="flex justify-center py-20"><Spinner size="lg" /></div>
        ) : (
          <DataTable 
            columns={columns} 
            data={orders} 
            emptyText="Không có đơn hàng nào phù hợp"
          />
        )}

        {/* Pagination */}
        {data && data.totalPages > 1 && (
          <div className="flex items-center justify-between px-4 py-3 border-t border-gray-200">
            <p className="text-sm text-gray-500">
              Trang {page + 1} / {data.totalPages} — Tổng {data.totalElements} đơn
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
              >
                ← Trước
              </Button>
              <Button
                variant="outline"
                size="sm"
                disabled={page >= data.totalPages - 1}
                onClick={() => setPage(p => p + 1)}
              >
                Sau →
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StaffOrderListPage;
