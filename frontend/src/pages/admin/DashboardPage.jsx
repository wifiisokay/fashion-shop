import { useState } from 'react';
import { Users, ShoppingBag, DollarSign, TrendingUp, Package } from 'lucide-react';
import { formatPrice } from '../../utils/format';

const MOCK_STATS = {
  totalRevenue: 125000000,
  totalOrders: 450,
  totalCustomers: 120,
  totalProducts: 85,
  recentOrders: [
    { id: 101, customer: 'Nguyễn Văn A', amount: 1250000, status: 'PENDING', date: '2026-03-30' },
    { id: 102, customer: 'Trần Thị B', amount: 850000, status: 'PROCESSING', date: '2026-03-29' },
    { id: 103, customer: 'Lê Văn C', amount: 2100000, status: 'SHIPPED', date: '2026-03-28' },
    { id: 104, customer: 'Phạm Thị D', amount: 450000, status: 'DELIVERED', date: '2026-03-27' },
  ]
};

const StatCard = ({ title, value, icon: Icon, trend }) => (
  <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm">
    <div className="flex items-center justify-between">
      <div>
        <p className="text-sm font-medium text-gray-500 mb-1">{title}</p>
        <h3 className="text-2xl font-bold text-gray-900">{value}</h3>
      </div>
      <div className="w-12 h-12 bg-gray-50 rounded-full flex items-center justify-center text-gray-700">
        <Icon className="w-6 h-6" />
      </div>
    </div>
    {trend && (
      <div className="mt-4 flex items-center text-sm">
        <TrendingUp className="w-4 h-4 text-green-500 mr-1" />
        <span className="text-green-500 font-medium">{trend}%</span>
        <span className="text-gray-500 ml-2">so với tháng trước</span>
      </div>
    )}
  </div>
);

const DashboardPage = () => {
  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Tổng quan</h1>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          title="Tổng doanh thu" 
          value={formatPrice(MOCK_STATS.totalRevenue)} 
          icon={DollarSign} 
          trend={12.5} 
        />
        <StatCard 
          title="Đơn hàng" 
          value={MOCK_STATS.totalOrders} 
          icon={ShoppingBag} 
          trend={8.2} 
        />
        <StatCard 
          title="Khách hàng" 
          value={MOCK_STATS.totalCustomers} 
          icon={Users} 
          trend={5.4} 
        />
        <StatCard 
          title="Sản phẩm" 
          value={MOCK_STATS.totalProducts} 
          icon={Package} 
        />
      </div>

      {/* Recent Orders */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-6 border-b border-gray-200">
          <h2 className="text-lg font-bold text-gray-900">Đơn hàng gần đây</h2>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-gray-50 text-gray-500">
              <tr>
                <th className="px-6 py-4 font-medium">Mã đơn</th>
                <th className="px-6 py-4 font-medium">Khách hàng</th>
                <th className="px-6 py-4 font-medium">Tổng tiền</th>
                <th className="px-6 py-4 font-medium">Trạng thái</th>
                <th className="px-6 py-4 font-medium">Ngày đặt</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {MOCK_STATS.recentOrders.map((order) => (
                <tr key={order.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 font-medium text-gray-900">#{order.id}</td>
                  <td className="px-6 py-4 text-gray-600">{order.customer}</td>
                  <td className="px-6 py-4 font-medium text-gray-900">{formatPrice(order.amount)}</td>
                  <td className="px-6 py-4">
                    <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                      order.status === 'DELIVERED' ? 'bg-green-100 text-green-800' :
                      order.status === 'SHIPPED' ? 'bg-indigo-100 text-indigo-800' :
                      order.status === 'PROCESSING' ? 'bg-blue-100 text-blue-800' :
                      'bg-yellow-100 text-yellow-800'
                    }`}>
                      {order.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-gray-500">{order.date}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
