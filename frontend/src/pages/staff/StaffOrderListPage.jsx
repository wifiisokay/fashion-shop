import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useStaffOrders } from '../../hooks/useStaffOrders';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, formatDate, formatOrderStatus } from '../../utils/format';
import { clsx } from 'clsx';
import { Eye } from 'lucide-react';

const MOCK_ORDERS = [
  { id: 101, customerName: 'Nguyễn Văn A', totalAmount: 1250000, status: 'PENDING', createdAt: '2026-03-30T10:00:00Z', paymentMethod: 'COD' },
  { id: 102, customerName: 'Trần Thị B', totalAmount: 850000, status: 'PROCESSING', createdAt: '2026-03-29T14:30:00Z', paymentMethod: 'VNPAY' },
  { id: 103, customerName: 'Lê Văn C', totalAmount: 2100000, status: 'SHIPPED', createdAt: '2026-03-28T09:15:00Z', paymentMethod: 'COD' },
  { id: 104, customerName: 'Phạm Thị D', totalAmount: 450000, status: 'DELIVERED', createdAt: '2026-03-27T16:45:00Z', paymentMethod: 'MOMO' },
  { id: 105, customerName: 'Hoàng Văn E', totalAmount: 3200000, status: 'CANCELLED', createdAt: '2026-03-26T11:20:00Z', paymentMethod: 'COD' },
];

const StaffOrderListPage = () => {
  const [filters, setFilters] = useState({ status: '', search: '' });
  const { data, isLoading, isError } = useStaffOrders(filters);

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
  };

  const orders = data?.content?.length ? data.content : MOCK_ORDERS.filter(o => {
    if (filters.status && o.status !== filters.status) return false;
    if (filters.search && !o.customerName.toLowerCase().includes(filters.search.toLowerCase()) && !o.id.toString().includes(filters.search)) return false;
    return true;
  });

  const columns = [
    { title: 'Mã đơn', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium">#{val}</span> },
    { title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName' },
    { title: 'Tổng tiền', dataIndex: 'totalAmount', key: 'totalAmount', render: (val) => <span className="font-medium text-gray-900">{formatPrice(val)}</span> },
    { title: 'Thanh toán', dataIndex: 'paymentMethod', key: 'paymentMethod' },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (val) => {
        const { label, color } = formatOrderStatus(val);
        const colorClass = color === 'yellow' ? 'bg-yellow-100 text-yellow-800' : 
                           color === 'blue' ? 'bg-blue-100 text-blue-800' :
                           color === 'indigo' ? 'bg-indigo-100 text-indigo-800' :
                           color === 'green' ? 'bg-green-100 text-green-800' :
                           color === 'red' ? 'bg-red-100 text-red-800' : 'bg-gray-100 text-gray-800';
        return <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', colorClass)}>{label}</span>;
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
            name="search"
            placeholder="Tìm mã đơn, tên KH..."
            value={filters.search}
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
            <option value="PENDING">Chờ xử lý</option>
            <option value="PROCESSING">Đang chuẩn bị</option>
            <option value="SHIPPED">Đang giao</option>
            <option value="DELIVERED">Đã giao</option>
            <option value="CANCELLED">Đã hủy</option>
            <option value="RETURNED">Đã hoàn trả</option>
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
