import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Search, Shield, ShieldAlert, User, Users, CheckCircle, XCircle } from 'lucide-react';
import DataTable from '../../components/admin/DataTable';
import { formatDate } from '../../utils/format';
import { userApi } from '../../api/userApi';
import { toast } from 'sonner';
import { useAuth } from '../../contexts/AuthContext';

const UserManagePage = () => {
  const queryClient = useQueryClient();
  const { user: authUser } = useAuth();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [page, setPage] = useState(0);

  // Fetch Stats
  const { data: statsData } = useQuery({
    queryKey: ['admin-user-stats'],
    queryFn: () => userApi.getUserStats().then(r => r.data?.data)
  });

  // Fetch Users
  const { data: usersData, isLoading } = useQuery({
    queryKey: ['admin-users', page, search, roleFilter],
    queryFn: () => userApi.getAdminUsers({
      page,
      size: 10,
      keyword: search || undefined,
      role: roleFilter || undefined
    }).then(r => r.data?.data),
    keepPreviousData: true
  });

  // Toggle Status
  const toggleMutation = useMutation({
    mutationFn: ({ id, status }) => userApi.toggleUserStatus(id, status),
    onSuccess: (res) => {
      toast.success(res.data?.message || 'Cập nhật trạng thái thành công');
      queryClient.invalidateQueries(['admin-users']);
      queryClient.invalidateQueries(['admin-user-stats']);
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Có lỗi xảy ra');
    }
  });

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium text-gray-500">#{val}</span> },
    { 
      title: 'Người dùng', 
      key: 'user', 
      render: (_, record) => (
        <div>
          <div className="font-medium text-gray-900">{record.fullName}</div>
          <div className="text-sm text-gray-500">{record.email}</div>
        </div>
      )
    },
    { 
      title: 'Vai trò', 
      dataIndex: 'role', 
      key: 'role',
      render: (val) => {
        const roleConfig = {
          ADMIN: { icon: ShieldAlert, color: 'text-red-600 bg-red-50 border-red-200' },
          EMPLOYEE: { icon: Shield, color: 'text-blue-600 bg-blue-50 border-blue-200' },
          CUSTOMER: { icon: User, color: 'text-gray-600 bg-gray-50 border-gray-200' },
        };
        const config = roleConfig[val] || roleConfig.CUSTOMER;
        const Icon = config.icon;
        return (
          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs font-medium border ${config.color}`}>
            <Icon className="w-3.5 h-3.5" />
            {val}
          </span>
        );
      }
    },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (val) => (
        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
          val === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
        }`}>
          {val === 'ACTIVE' ? 'Hoạt động' : 'Khóa'}
        </span>
      )
    },
    { title: 'Ngày tham gia', dataIndex: 'createdAt', key: 'createdAt', render: (val) => formatDate(val) },
    { 
      title: 'Hành động', 
      dataIndex: 'id', 
      key: 'action',
      render: (id, record) => {
        const isCurrentUser = authUser?.userId === id;
        
        return (
          <select 
            className="text-sm border-gray-300 rounded-md py-1 pl-2 pr-8 focus:ring-black focus:border-black disabled:bg-gray-100 disabled:text-gray-400"
            value={record.status}
            onChange={(e) => {
              if (window.confirm(`Bạn có chắc muốn ${e.target.value === 'LOCKED' ? 'khóa' : 'mở khóa'} tài khoản này?`)) {
                toggleMutation.mutate({ id, status: e.target.value });
              }
            }}
            disabled={toggleMutation.isLoading || isCurrentUser}
            title={isCurrentUser ? "Bạn không thể khóa tài khoản của chính mình" : ""}
          >
            <option value="ACTIVE">Hoạt động</option>
            <option value="LOCKED">Khóa tài khoản</option>
          </select>
        );
      }
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Người dùng</h1>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Tổng Người dùng</p>
              <h3 className="text-2xl font-bold text-gray-900 mt-1">{statsData?.totalUsers || 0}</h3>
            </div>
            <div className="w-10 h-10 bg-indigo-50 rounded-full flex items-center justify-center">
              <Users className="w-5 h-5 text-indigo-600" />
            </div>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Khách hàng</p>
              <h3 className="text-2xl font-bold text-gray-900 mt-1">{statsData?.customerCount || 0}</h3>
            </div>
            <div className="w-10 h-10 bg-blue-50 rounded-full flex items-center justify-center">
              <User className="w-5 h-5 text-blue-600" />
            </div>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-gray-500">Quản trị / Nhân viên</p>
              <h3 className="text-2xl font-bold text-gray-900 mt-1">{(statsData?.adminCount || 0) + (statsData?.employeeCount || 0)}</h3>
            </div>
            <div className="w-10 h-10 bg-purple-50 rounded-full flex items-center justify-center">
              <Shield className="w-5 h-5 text-purple-600" />
            </div>
          </div>
        </div>
        <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
          <div className="flex flex-col gap-2">
            <div className="flex justify-between items-center text-sm">
              <span className="flex items-center text-green-600"><CheckCircle className="w-4 h-4 mr-1"/> Hoạt động</span>
              <span className="font-semibold text-gray-900">{statsData?.activeUsers || 0}</span>
            </div>
            <div className="flex justify-between items-center text-sm">
              <span className="flex items-center text-red-600"><XCircle className="w-4 h-4 mr-1"/> Đã khóa</span>
              <span className="font-semibold text-gray-900">{statsData?.lockedUsers || 0}</span>
            </div>
          </div>
        </div>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-4 border-b border-gray-200 flex flex-col sm:flex-row gap-4">
          <div className="relative flex-1 max-w-md">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-5 w-5 text-gray-400" />
            </div>
            <input
              type="text"
              placeholder="Tìm kiếm tên, email..."
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-black focus:border-black sm:text-sm"
            />
          </div>
          <select
            value={roleFilter}
            onChange={(e) => {
              setRoleFilter(e.target.value);
              setPage(0);
            }}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black py-2 pl-3 pr-10 border"
          >
            <option value="">Tất cả vai trò</option>
            <option value="ADMIN">Admin</option>
            <option value="EMPLOYEE">Nhân viên</option>
            <option value="CUSTOMER">Khách hàng</option>
          </select>
        </div>
        <DataTable 
          columns={columns} 
          data={usersData?.content || []} 
          isLoading={isLoading}
          emptyText="Không tìm thấy người dùng nào"
          pagination={true}
          currentPage={page}
          totalPages={usersData?.totalPages || 1}
          onPageChange={setPage}
        />
      </div>
    </div>
  );
};

export default UserManagePage;
