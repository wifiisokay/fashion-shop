import { useQuery } from '@tanstack/react-query';
import { Users, ShoppingBag, DollarSign, Package, AlertCircle } from 'lucide-react';
import { formatPrice } from '../../utils/format';
import { orderApi } from '../../api/orderApi';
import dashboardApi from '../../api/dashboardApi';
import Spinner from '../../components/ui/Spinner';
import dayjs from 'dayjs';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Legend
} from 'recharts';

const COLORS = ['#10B981', '#3B82F6', '#F59E0B', '#EF4444', '#8B5CF6', '#6B7280'];

const StatCard = ({ title, value, icon: Icon }) => (
  <div className="bg-white p-6 rounded-xl border border-gray-200 shadow-sm flex items-center justify-between">
    <div>
      <p className="text-sm font-medium text-gray-500 mb-1">{title}</p>
      <h3 className="text-2xl font-bold text-gray-900">{value}</h3>
    </div>
    <div className="w-12 h-12 bg-indigo-50 rounded-full flex items-center justify-center text-indigo-600">
      <Icon className="w-6 h-6" />
    </div>
  </div>
);

const CustomTooltip = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-white p-3 border border-gray-200 shadow-lg rounded-lg">
        <p className="text-sm text-gray-600 mb-1">{dayjs(label).format('DD/MM/YYYY')}</p>
        <p className="text-sm font-bold text-indigo-600">
          {formatPrice(payload[0].value)}
        </p>
      </div>
    );
  }
  return null;
};

const DashboardPage = () => {
  // Fetch Dashboard Stats
  const { data: statsData, isLoading: isLoadingStats, isError: isErrorStats } = useQuery({
    queryKey: ['adminDashboardStats'],
    queryFn: async () => {
      const res = await dashboardApi.getStats();
      return res.data?.data;
    }
  });

  // Fetch Recent Orders
  const { data: recentOrdersData, isLoading: isLoadingOrders } = useQuery({
    queryKey: ['adminRecentOrders'],
    queryFn: async () => {
      const res = await orderApi.getAllOrders({ page: 0, size: 5, sort: 'createdAt,desc' });
      return res.data?.data;
    }
  });

  if (isLoadingStats || isLoadingOrders) {
    return (
      <div className="flex justify-center items-center h-64">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isErrorStats || !statsData) {
    return (
      <div className="bg-red-50 text-red-600 p-4 rounded-lg flex items-center">
        <AlertCircle className="w-5 h-5 mr-2" />
        Lỗi khi tải dữ liệu thống kê. Vui lòng thử lại.
      </div>
    );
  }

  const { totals, revenueTrend, orderStatusDistribution } = statsData;
  const recentOrders = recentOrdersData?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-gray-900">Tổng quan hệ thống</h1>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard 
          title="Tổng doanh thu" 
          value={formatPrice(totals.totalRevenue || 0)} 
          icon={DollarSign} 
        />
        <StatCard 
          title="Tổng đơn hàng" 
          value={totals.totalOrders || 0} 
          icon={ShoppingBag} 
        />
        <StatCard 
          title="Khách hàng" 
          value={totals.totalCustomers || 0} 
          icon={Users} 
        />
        <StatCard 
          title="Sản phẩm đang bán" 
          value={totals.totalProducts || 0} 
          icon={Package} 
        />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Revenue Line Chart */}
        <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-6">Xu hướng doanh thu (30 ngày)</h2>
          <div className="h-80">
            {revenueTrend && revenueTrend.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={revenueTrend} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                  <XAxis 
                    dataKey="date" 
                    tickFormatter={(val) => dayjs(val).format('DD/MM')}
                    stroke="#9CA3AF"
                    fontSize={12}
                    tickMargin={10}
                  />
                  <YAxis 
                    tickFormatter={(val) => `${val / 1000000}M`}
                    stroke="#9CA3AF"
                    fontSize={12}
                    width={60}
                  />
                  <Tooltip content={<CustomTooltip />} />
                  <Line 
                    type="monotone" 
                    dataKey="revenue" 
                    stroke="#4F46E5" 
                    strokeWidth={3}
                    dot={{ r: 4, strokeWidth: 2 }}
                    activeDot={{ r: 6 }}
                  />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center text-gray-500">
                Chưa có dữ liệu doanh thu
              </div>
            )}
          </div>
        </div>

        {/* Order Status Pie Chart */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900 mb-6">Trạng thái đơn hàng</h2>
          <div className="h-80">
            {orderStatusDistribution && orderStatusDistribution.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={orderStatusDistribution}
                    cx="50%"
                    cy="45%"
                    innerRadius={60}
                    outerRadius={90}
                    paddingAngle={2}
                    dataKey="count"
                    nameKey="status"
                  >
                    {orderStatusDistribution.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                    ))}
                  </Pie>
                  <Tooltip 
                    formatter={(value, name) => [value + ' đơn', name]}
                    contentStyle={{ borderRadius: '8px', border: '1px solid #E5E7EB' }}
                  />
                  <Legend verticalAlign="bottom" height={36} iconType="circle" />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex h-full items-center justify-center text-gray-500">
                Chưa có đơn hàng nào
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Recent Orders */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-6 border-b border-gray-200 flex justify-between items-center">
          <h2 className="text-lg font-bold text-gray-900">Đơn hàng mới nhất</h2>
        </div>
        <div className="overflow-x-auto">
          {recentOrders.length > 0 ? (
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
                {recentOrders.map((order) => (
                  <tr key={order.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-6 py-4 font-medium text-gray-900">#{order.id}</td>
                    <td className="px-6 py-4 text-gray-600">{order.customerName}</td>
                    <td className="px-6 py-4 font-medium text-indigo-600">
                      {formatPrice(order.totalAmount)}
                    </td>
                    <td className="px-6 py-4">
                      <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
                        order.status === 'DELIVERED' ? 'bg-green-100 text-green-800' :
                        order.status === 'SHIPPED' ? 'bg-indigo-100 text-indigo-800' :
                        order.status === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
                        order.status === 'CANCELLED' ? 'bg-red-100 text-red-800' :
                        'bg-blue-100 text-blue-800'
                      }`}>
                        {order.status}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-gray-500">
                      {dayjs(order.createdAt).format('HH:mm DD/MM/YYYY')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="p-6 text-center text-gray-500">
              Không có đơn hàng nào
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
