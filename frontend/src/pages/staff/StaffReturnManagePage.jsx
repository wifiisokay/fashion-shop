import { useState } from 'react';
import { useReturns } from '../../hooks/useReturns';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatReturnType, formatDate } from '../../utils/format';
import { clsx } from 'clsx';

const StaffReturnManagePage = () => {
  const [filters, setFilters] = useState({ status: '', return_type: '' });
  const { data, isLoading, isError } = useReturns(filters);

  const handleFilterChange = (e) => {
    const { name, value } = e.target;
    setFilters((prev) => ({ ...prev, [name]: value }));
  };

  const columns = [
    { title: 'Mã đơn', dataIndex: 'orderId', key: 'orderId', render: (val) => <span className="font-medium">#{val}</span> },
    { title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName' },
    { 
      title: 'Loại trả', 
      dataIndex: 'returnType', 
      key: 'returnType',
      render: (val) => {
        const { label, color } = formatReturnType(val);
        const colorClass = color === 'red' ? 'bg-red-100 text-red-800' : 
                           color === 'blue' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800';
        return <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', colorClass)}>{label}</span>;
      }
    },
    { title: 'Lý do', dataIndex: 'reason', key: 'reason' },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (val) => {
        const statusMap = {
          PENDING: { label: 'Chờ xử lý', color: 'bg-yellow-100 text-yellow-800' },
          APPROVED: { label: 'Đã duyệt', color: 'bg-green-100 text-green-800' },
          REJECTED: { label: 'Từ chối', color: 'bg-red-100 text-red-800' },
        };
        const statusInfo = statusMap[val] || { label: val, color: 'bg-gray-100 text-gray-800' };
        return <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', statusInfo.color)}>{statusInfo.label}</span>;
      }
    },
    { title: 'Ngày tạo', dataIndex: 'createdAt', key: 'createdAt', render: (val) => formatDate(val) },
    { 
      title: 'Hành động', 
      dataIndex: 'id', 
      key: 'action',
      render: (id) => (
        <Button variant="ghost" size="sm" onClick={() => console.log('View detail', id)}>
          Xem chi tiết
        </Button>
      )
    },
  ];

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[400px]">
        <p className="text-red-500 font-medium">Không thể tải dữ liệu đơn trả hàng</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Đổi/Trả hàng</h1>
        
        <div className="flex flex-col sm:flex-row gap-3">
          <select 
            name="status" 
            value={filters.status} 
            onChange={handleFilterChange}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border"
          >
            <option value="">Tất cả trạng thái</option>
            <option value="PENDING">Chờ xử lý</option>
            <option value="APPROVED">Đã duyệt</option>
            <option value="REJECTED">Từ chối</option>
          </select>
          
          <select 
            name="return_type" 
            value={filters.return_type} 
            onChange={handleFilterChange}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border"
          >
            <option value="">Tất cả loại trả</option>
            <option value="REFUND">Hoàn tiền</option>
            <option value="EXCHANGE">Đổi hàng</option>
          </select>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <DataTable 
          columns={columns} 
          data={data?.content || []} 
          loading={isLoading} 
          emptyText="Không có đơn đổi/trả nào phù hợp"
        />
      </div>
    </div>
  );
};

export default StaffReturnManagePage;
