import { useState } from 'react';
import { Search, Shield, ShieldAlert, User } from 'lucide-react';
import DataTable from '../../components/admin/DataTable';
import { formatDate } from '../../utils/format';

const MOCK_USERS = [
  { id: 1, name: 'Admin System', email: 'admin@fashionshop.com', role: 'ADMIN', status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' },
  { id: 2, name: 'Staff Support', email: 'staff@fashionshop.com', role: 'EMPLOYEE', status: 'ACTIVE', createdAt: '2026-02-15T10:30:00Z' },
  { id: 3, name: 'Nguyễn Văn A', email: 'nguyenvana@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-10T08:45:00Z' },
  { id: 4, name: 'Trần Thị B', email: 'tranthib@gmail.com', role: 'CUSTOMER', status: 'INACTIVE', createdAt: '2026-03-20T14:20:00Z' },
  { id: 5, name: 'Lê Văn C', email: 'levanc@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-25T09:10:00Z' },
  { id: 6, name: 'Phạm Thị D', email: 'phamthid@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-26T10:15:00Z' },
  { id: 7, name: 'Hoàng Văn E', email: 'hoangvane@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-27T11:20:00Z' },
  { id: 8, name: 'Vũ Thị F', email: 'vuthif@gmail.com', role: 'CUSTOMER', status: 'INACTIVE', createdAt: '2026-03-28T12:25:00Z' },
  { id: 9, name: 'Đặng Văn G', email: 'dangvang@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-29T13:30:00Z' },
  { id: 10, name: 'Bùi Thị H', email: 'buithih@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-30T14:35:00Z' },
  { id: 11, name: 'Đỗ Văn I', email: 'dovani@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-03-31T15:40:00Z' },
  { id: 12, name: 'Hồ Thị K', email: 'hothik@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-04-01T16:45:00Z' },
  { id: 13, name: 'Ngô Văn L', email: 'ngovanl@gmail.com', role: 'CUSTOMER', status: 'INACTIVE', createdAt: '2026-04-02T17:50:00Z' },
  { id: 14, name: 'Dương Thị M', email: 'duongthim@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-04-03T18:55:00Z' },
  { id: 15, name: 'Lý Văn N', email: 'lyvann@gmail.com', role: 'CUSTOMER', status: 'ACTIVE', createdAt: '2026-04-04T20:00:00Z' },
];

const UserManagePage = () => {
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('');

  const filteredUsers = MOCK_USERS.filter(u => {
    const matchesSearch = u.name.toLowerCase().includes(search.toLowerCase()) || u.email.toLowerCase().includes(search.toLowerCase());
    const matchesRole = roleFilter ? u.role === roleFilter : true;
    return matchesSearch && matchesRole;
  });

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium text-gray-500">#{val}</span> },
    { 
      title: 'Người dùng', 
      key: 'user', 
      render: (_, record) => (
        <div>
          <div className="font-medium text-gray-900">{record.name}</div>
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
        const config = roleConfig[val];
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
      render: (id, record) => (
        <select 
          className="text-sm border-gray-300 rounded-md py-1 pl-2 pr-8 focus:ring-black focus:border-black"
          defaultValue={record.status}
          onChange={(e) => alert(`Đổi trạng thái user ${id} thành ${e.target.value}`)}
        >
          <option value="ACTIVE">Hoạt động</option>
          <option value="INACTIVE">Khóa tài khoản</option>
        </select>
      )
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Người dùng</h1>
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
              onChange={(e) => setSearch(e.target.value)}
              className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-black focus:border-black sm:text-sm"
            />
          </div>
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
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
          data={filteredUsers} 
          emptyText="Không tìm thấy người dùng nào"
          pagination={true}
          pageSize={5}
        />
      </div>
    </div>
  );
};

export default UserManagePage;
