import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useStaffOrders } from '../../hooks/useStaffOrders';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, formatDate, formatOrderStatus } from '../../utils/format';
import { clsx } from 'clsx';
import { Eye } from 'lucide-react';

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
  const [filters, setFilters] = useState({ status: '', keyword: '' });
  const { data, isLoading } = useStaffOrders(filters);

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
  };

  const orders = data?.content || [];

  const columns = [
    { title: 'Mã đơn', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium">#{val}</span> },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', key: 'totalAmount', render: (val) => <span className="font-medium text-gray-900">{formatPrice(val)}</span> },
    { title: 'Thanh toán', dataIndex: 'paymentMethod', key: 'paymentMethod', render: (val) => val === 'COD' ? 'COD' : 'VNPAY' },
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

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Đơn hàng</h1>
        
        <div className="flex flex-col sm:flex-row gap-3">
          <input 
            type="text"
            name="keyword"
            placeholder="Tìm mã đơn..."
            value={filters.keyword}
            onChange={handleFilterChange}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border min-w-[200px]"
          />
          <select 
            name="status" 
            value={filters.status} 
            onChange={handleFilterChange}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="AWAITING_PAYMENT">Chờ thanh toán</option>
            <option value="PENDING">Chờ xác nhận</option>
            <option value="CONFIRMED">Đã xác nhận</option>
            <option value="SHIPPING">Đang giao</option>
            <option value="DELIVERED">Đã giao</option>
            <option value="COMPLETED">Hoàn thành</option>
            <option value="CANCELLED">Đã hủy</option>
            <option value="RETURN_REQUESTED">Yêu cầu trả hàng</option>
          </select>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <DataTable 
          columns={columns} 
          data={orders} 
          loading={isLoading} 
          emptyText="Không có đơn hàng nào phù hợp"
        />
      </div>
    </div>
  );
};

export default StaffOrderListPage;
