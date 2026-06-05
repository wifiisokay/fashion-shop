import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { clsx } from 'clsx';
import { 
  Users, 
  ShoppingBag, 
  DollarSign, 
  Package, 
  AlertCircle, 
  RotateCcw, 
  CheckCircle,
  XCircle,
  Calendar,
  Truck,
  Clock,
  AlertTriangle,
  Eye,
  TrendingUp,
  RefreshCw,
  ArrowRight,
  Edit
} from 'lucide-react';
import { formatPrice } from '../../utils/format';
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
  BarChart,
  Bar,
  Cell,
  PieChart,
  Pie,
  Legend
} from 'recharts';

// Preset date options
const PRESETS = [
  { id: 'today', label: 'Hôm nay' },
  { id: '7days', label: '7 ngày' },
  { id: '30days', label: '30 ngày' },
  { id: 'thisMonth', label: 'Tháng này' },
  { id: 'custom', label: 'Tùy chọn' }
];

// Order status styling map
const ORDER_STATUS_MAP = {
  'PENDING': { label: 'Chờ xử lý', color: '#F59E0B' },
  'CONFIRMED': { label: 'Đã xác nhận', color: '#3B82F6' },
  'SHIPPING': { label: 'Đang giao', color: '#6366F1' },
  'DELIVERED': { label: 'Đã giao', color: '#10B981' },
  'COMPLETED': { label: 'Hoàn thành', color: '#047857' },
  'CANCELLED': { label: 'Đã hủy', color: '#EF4444' },
  'RETURNED': { label: 'Đã hoàn trả', color: '#EC4899' },
  'RETURN_REQUESTED': { label: 'Yêu cầu trả hàng', color: '#F97316' },
  'RETURNING': { label: 'Đang trả hàng', color: '#A855F7' }
};

// Return status styling map
const RETURN_STATUS_MAP = {
  'REQUESTED': { label: 'Chờ duyệt', color: '#EF4444', badgeClass: 'bg-red-50 text-red-700 border-red-200' },
  'APPROVED': { label: 'Đã duyệt', color: '#F59E0B', badgeClass: 'bg-amber-50 text-amber-700 border-amber-200' },
  'RECEIVED': { label: 'Đã nhận', color: '#3B82F6', badgeClass: 'bg-blue-50 text-blue-700 border-blue-200' },
  'COMPLETED': { label: 'Đã hoàn tiền', color: '#10B981', badgeClass: 'bg-green-50 text-green-700 border-green-200' },
  'REJECTED': { label: 'Từ chối', color: '#6B7280', badgeClass: 'bg-gray-50 text-gray-700 border-gray-200' }
};

// StatCard Component
const StatCard = ({ title, value, icon: Icon, className, valueClassName, iconClassName, children }) => (
  <div className={clsx("bg-white p-5 rounded-2xl border border-gray-200 shadow-sm flex flex-col justify-between transition-all duration-300 hover:shadow-md hover:-translate-y-0.5", className)}>
    <div className="flex items-start justify-between w-full">
      <div>
        <p className="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-1">{title}</p>
        <h3 className={clsx("text-2xl font-bold text-gray-900 tracking-tight", valueClassName)}>{value}</h3>
      </div>
      <div className={clsx("w-10 h-10 rounded-xl flex items-center justify-center shadow-sm", iconClassName || "bg-indigo-50 text-indigo-600")}>
        <Icon className="w-5 h-5" />
      </div>
    </div>
    {children}
  </div>
);

// Revenue Tooltip Component
const CustomRevenueTooltip = ({ active, payload, label }) => {
  if (active && payload && payload.length) {
    const dataPoint = payload[0].payload;
    return (
      <div className="bg-white p-4 border border-gray-200 shadow-xl rounded-xl">
        <p className="text-xs font-bold text-gray-400 mb-1">{dayjs(label).format('DD/MM/YYYY')}</p>
        <div className="space-y-1">
          <p className="text-sm font-bold text-indigo-600">
            Doanh thu thực nhận: {formatPrice(payload[0].value)}
          </p>
          <p className="text-xs text-gray-500 font-medium">
            Số đơn COMPLETED + PAID: {dataPoint.orderCount || 0} đơn
          </p>
        </div>
      </div>
    );
  }
  return null;
};

// General Chart Card Wrapper
const ChartCard = ({ title, children, empty, emptyMessage = "Chưa có dữ liệu thống kê" }) => (
  <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 flex flex-col h-full">
    <h2 className="text-base font-bold text-gray-900 mb-4 tracking-tight">{title}</h2>
    <div className="flex-1 min-h-[300px] h-80 relative flex items-center justify-center">
      {empty ? (
        <div className="text-sm font-medium text-gray-400 flex flex-col items-center gap-2">
          <AlertCircle className="w-8 h-8 text-gray-300" />
          <span>{emptyMessage}</span>
        </div>
      ) : (
        children
      )}
    </div>
  </div>
);

const DashboardPage = () => {
  // 1. State for Date Filters
  const [preset, setPreset] = useState('thisMonth');
  
  const defaultStartDate = useMemo(() => dayjs().startOf('month').format('YYYY-MM-DD'), []);
  const defaultEndDate = useMemo(() => dayjs().format('YYYY-MM-DD'), []);
  
  const [startDate, setStartDate] = useState(defaultStartDate);
  const [endDate, setEndDate] = useState(defaultEndDate);
  
  const [tempStartDate, setTempStartDate] = useState(defaultStartDate);
  const [tempEndDate, setTempEndDate] = useState(defaultEndDate);
  const [validationError, setValidationError] = useState('');

  // 2. Query key matches ['adminDashboard', startDate, endDate]
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['adminDashboard', startDate, endDate],
    queryFn: async () => {
      const res = await dashboardApi.getAdminDashboard({ from: startDate, to: endDate });
      return res.data?.data;
    },
    keepPreviousData: true,
    retry: 1
  });

  // 3. Preset change handler
  const handlePresetChange = (selectedPreset) => {
    setPreset(selectedPreset);
    const todayStr = dayjs().format('YYYY-MM-DD');
    setValidationError('');

    if (selectedPreset === 'today') {
      setStartDate(todayStr);
      setEndDate(todayStr);
      setTempStartDate(todayStr);
      setTempEndDate(todayStr);
    } else if (selectedPreset === '7days') {
      const start = dayjs().subtract(6, 'day').format('YYYY-MM-DD');
      setStartDate(start);
      setEndDate(todayStr);
      setTempStartDate(start);
      setTempEndDate(todayStr);
    } else if (selectedPreset === '30days') {
      const start = dayjs().subtract(29, 'day').format('YYYY-MM-DD');
      setStartDate(start);
      setEndDate(todayStr);
      setTempStartDate(start);
      setTempEndDate(todayStr);
    } else if (selectedPreset === 'thisMonth') {
      setStartDate(defaultStartDate);
      setEndDate(defaultEndDate);
      setTempStartDate(defaultStartDate);
      setTempEndDate(defaultEndDate);
    } else if (selectedPreset === 'custom') {
      setTempStartDate(startDate);
      setTempEndDate(endDate);
    }
  };

  // 4. Custom date range apply handler
  const handleApplyCustomDates = () => {
    if (!tempStartDate || !tempEndDate) {
      setValidationError('Vui lòng chọn đầy đủ từ ngày và đến ngày.');
      return;
    }
    const start = dayjs(tempStartDate);
    const end = dayjs(tempEndDate);

    if (start.isAfter(end)) {
      setValidationError('Ngày bắt đầu không được sau ngày kết thúc.');
      return;
    }
    if (end.diff(start, 'day') > 365) {
      setValidationError('Khoảng thời gian tối đa là 365 ngày.');
      return;
    }

    setValidationError('');
    setStartDate(tempStartDate);
    setEndDate(tempEndDate);
  };

  // 5. Data parser for charts and tables
  const { overview, charts, products, returns } = data || {};

  // Sort daily revenue chronologically
  const dailyRevenueData = useMemo(() => {
    if (!charts?.dailyRevenue) return [];
    return [...charts.dailyRevenue].sort((a, b) => dayjs(a.date).diff(dayjs(b.date)));
  }, [charts?.dailyRevenue]);

  // Map order status distribution to recharts list
  const orderChartData = useMemo(() => {
    const rawDist = charts?.orderStatusDistribution || {};
    return Object.entries(rawDist)
      .map(([status, count]) => {
        const mapping = ORDER_STATUS_MAP[status] || { label: status, color: '#6B7280' };
        return {
          statusKey: status,
          name: mapping.label,
          value: count,
          color: mapping.color
        };
      })
      .filter(item => item.value > 0);
  }, [charts?.orderStatusDistribution]);

  // Map return status distribution to recharts list
  const returnChartData = useMemo(() => {
    const rawDist = charts?.returnStatusDistribution || {};
    return Object.entries(rawDist)
      .map(([status, count]) => {
        const mapping = RETURN_STATUS_MAP[status] || { label: status, color: '#6B7280' };
        return {
          statusKey: status,
          name: mapping.label,
          value: count,
          color: mapping.color
        };
      })
      .filter(item => item.value > 0);
  }, [charts?.returnStatusDistribution]);

  const topSellingProductsList = products?.topSellingProducts || [];
  const returnsQueueList = returns?.queue || [];

  // Rendering Loading Skeleton
  if (isLoading) {
    return (
      <div className="space-y-6 animate-pulse">
        {/* Header Skeleton */}
        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div className="h-7 w-48 bg-gray-200 rounded-lg" />
          <div className="h-10 w-80 bg-gray-200 rounded-lg" />
        </div>
        
        {/* Stats Skeleton */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-10 gap-5">
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-2 lg:col-span-2 xl:col-span-2" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-1 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-2 xl:col-span-1" />
          <div className="h-32 bg-gray-200 rounded-2xl col-span-1 sm:col-span-1 lg:col-span-2 xl:col-span-1" />
        </div>

        {/* Charts Skeleton */}
        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="h-80 bg-gray-200 rounded-2xl xl:col-span-2" />
          <div className="h-80 bg-gray-200 rounded-2xl" />
        </div>
      </div>
    );
  }

  // Rendering Error State
  if (isError) {
    return (
      <div className="bg-rose-50/50 text-rose-700 p-6 rounded-2xl border border-rose-100 flex flex-col items-center justify-center text-center max-w-2xl mx-auto my-12">
        <AlertCircle className="w-12 h-12 text-rose-500 mb-3" />
        <h3 className="text-lg font-bold mb-1">Không thể tải dữ liệu thống kê</h3>
        <p className="text-sm text-rose-600/80 mb-4">Đã xảy ra lỗi khi giao tiếp với hệ thống backend. Vui lòng kiểm tra lại kết nối và thử lại.</p>
        <button 
          onClick={() => refetch()}
          className="bg-rose-600 hover:bg-rose-700 text-white text-sm font-semibold px-4 py-2 rounded-xl transition-all duration-200 shadow-sm shadow-rose-600/10"
        >
          Thử lại
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 1. Header and Date Filter */}
      <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm flex flex-col gap-4">
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
          <div>
            <h1 className="text-2xl font-black text-gray-900 tracking-tight flex items-center gap-2">
              <TrendingUp className="w-6 h-6 text-indigo-600" />
              <span>Admin Dashboard tổng quan</span>
            </h1>
            <p className="text-xs text-gray-400 mt-0.5">Thống kê hoạt động của hệ thống cửa hàng thời trang</p>
          </div>

          {/* Date presets selection */}
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex p-1 bg-gray-50 border border-gray-200 rounded-xl">
              {PRESETS.map((p) => (
                <button
                  key={p.id}
                  onClick={() => handlePresetChange(p.id)}
                  className={clsx(
                    "px-3 py-1.5 text-xs font-semibold rounded-lg transition-all duration-200",
                    preset === p.id 
                      ? "bg-white text-indigo-600 shadow-sm border-gray-200/50" 
                      : "text-gray-500 hover:text-gray-900"
                  )}
                >
                  {p.label}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Custom date range inputs */}
        {preset === 'custom' && (
          <div className="flex flex-wrap items-end gap-3 p-4 bg-gray-50/50 border border-gray-150 rounded-xl transition-all duration-300">
            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Từ ngày</label>
              <div className="relative">
                <input
                  type="date"
                  value={tempStartDate}
                  onChange={(e) => setTempStartDate(e.target.value)}
                  className="bg-white border border-gray-200 rounded-xl px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                />
              </div>
            </div>

            <div className="space-y-1">
              <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Đến ngày</label>
              <div className="relative">
                <input
                  type="date"
                  value={tempEndDate}
                  onChange={(e) => setTempEndDate(e.target.value)}
                  className="bg-white border border-gray-200 rounded-xl px-3 py-2 text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-all"
                />
              </div>
            </div>

            <button
              onClick={handleApplyCustomDates}
              className="bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold px-4 py-2.5 rounded-xl transition-all duration-200 shadow-sm hover:shadow shadow-indigo-600/10 h-[38px] flex items-center gap-1.5"
            >
              <Calendar className="w-3.5 h-3.5" />
              <span>Áp dụng</span>
            </button>

            {validationError && (
              <span className="text-xs font-semibold text-rose-600 mb-2.5 self-center">{validationError}</span>
            )}
          </div>
        )}

        {/* Informative description on revenue method */}
        <div className="flex items-center gap-2 text-xs text-blue-600 bg-blue-50/40 px-3.5 py-2.5 rounded-xl border border-blue-100/50">
          <AlertCircle className="w-4 h-4 text-blue-500 flex-shrink-0" />
          <span className="font-medium">
            Chỉ tính doanh thu thực nhận từ đơn COMPLETED và payment PAID. Biểu đồ ưu tiên ngày hoàn tất đơn.
          </span>
        </div>
      </div>

      {/* 2. Overview Stats Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-10 gap-5">
        
        {/* Thẻ Doanh thu thực nhận - nổi bật nhất */}
        <div className="bg-gradient-to-br from-indigo-900 via-indigo-800 to-slate-900 text-white p-6 rounded-2xl border border-indigo-950 shadow-md shadow-indigo-950/10 flex flex-col justify-between transition-all duration-300 hover:-translate-y-1 hover:shadow-xl hover:shadow-indigo-950/20 col-span-1 sm:col-span-2 lg:col-span-2 xl:col-span-2 min-h-[160px]">
          <div>
            <div className="flex items-center justify-between w-full mb-1">
              <span className="text-xs font-bold text-indigo-200 uppercase tracking-widest">Doanh thu thực nhận</span>
              <div className="w-9 h-9 rounded-xl bg-indigo-500/20 text-indigo-300 flex items-center justify-center backdrop-blur-sm">
                <DollarSign className="w-5 h-5" />
              </div>
            </div>
            <h3 className="text-2xl font-black tracking-tight">{formatPrice(overview?.netRevenue || 0)}</h3>
          </div>

          <div className="border-t border-indigo-700/40 pt-3 mt-4 flex flex-col gap-1.5 text-xs text-indigo-200 font-medium">
            <div className="flex justify-between items-center">
              <span>Đơn COMPLETED + PAID:</span>
              <span className="font-bold text-emerald-300">{formatPrice(overview?.finalizedGrossRevenue || 0)}</span>
            </div>
            <div className="flex justify-between items-center">
              <span>Tổng tiền đã hoàn:</span>
              <span className="font-bold text-rose-300">{formatPrice(overview?.refundedAmount || 0)}</span>
            </div>
            <div className="flex justify-between items-center border-t border-indigo-700/20 pt-1.5 mt-1 text-[10px] text-indigo-300/80">
              <span>Số đơn đã chốt doanh thu:</span>
              <span className="font-bold">{overview?.finalizedOrderCount || 0} đơn</span>
            </div>
          </div>
        </div>

        {/* Đơn chờ xử lý */}
        <StatCard
          title="Đơn chờ xử lý"
          value={overview?.pendingOrderCount || 0}
          icon={Clock}
          className={clsx(overview?.pendingOrderCount > 0 && "border-amber-200 bg-amber-50/10 shadow-amber-50/5")}
          iconClassName={clsx(
            overview?.pendingOrderCount > 0 
              ? "bg-amber-50 text-amber-600 border border-amber-100" 
              : "bg-gray-50 text-gray-600"
          )}
          valueClassName={clsx(overview?.pendingOrderCount > 0 && "text-amber-700")}
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Đơn mới/chờ xác nhận</p>
        </StatCard>

        {/* Đơn đang giao */}
        <StatCard
          title="Đơn đang giao"
          value={overview?.shippingOrderCount || 0}
          icon={Truck}
          iconClassName="bg-blue-50 text-blue-600 border border-blue-100"
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Đơn đang giao hàng</p>
        </StatCard>

        {/* Đơn hoàn thành */}
        <StatCard
          title="Đơn hoàn thành"
          value={overview?.completedOrderCount || 0}
          icon={CheckCircle}
          iconClassName="bg-emerald-50 text-emerald-600 border border-emerald-100"
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Đơn hàng đã hoàn tất</p>
        </StatCard>

        {/* Đơn hủy */}
        <StatCard
          title="Đơn hủy"
          value={overview?.cancelledOrders || 0}
          icon={XCircle}
          iconClassName="bg-red-50 text-red-600 border border-red-100"
          valueClassName={clsx((overview?.cancelledOrders || 0) > 0 && "text-red-700")}
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Status = CANCELLED</p>
        </StatCard>

        {/* Đơn hoàn hàng */}
        <StatCard
          title="Đơn hoàn hàng"
          value={overview?.returnedOrders || 0}
          icon={RotateCcw}
          iconClassName="bg-pink-50 text-pink-600 border border-pink-100"
          valueClassName={clsx((overview?.returnedOrders || 0) > 0 && "text-pink-700")}
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Status = RETURNED</p>
        </StatCard>

        {/* Trả hàng chờ xử lý */}
        <StatCard
          title="Trả hàng chờ xử lý"
          value={overview?.pendingReturnCount || 0}
          icon={RefreshCw}
          className={clsx(overview?.pendingReturnCount > 0 && "border-rose-200 bg-rose-50/10 shadow-rose-50/5")}
          iconClassName={clsx(
            overview?.pendingReturnCount > 0 
              ? "bg-rose-50 text-rose-600 border border-rose-100" 
              : "bg-gray-50 text-gray-600"
          )}
          valueClassName={clsx(overview?.pendingReturnCount > 0 && "text-rose-700")}
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Yêu cầu hoàn trả mới</p>
        </StatCard>

        {/* Cảnh báo tồn kho */}
        <StatCard
          title="Cảnh báo tồn kho"
          value={(data?.stockAlerts?.lowStockCount !== undefined ? data.stockAlerts.lowStockCount : (overview?.lowStockProductCount || 0)) + (data?.stockAlerts?.outOfStockCount || 0)}
          icon={AlertTriangle}
          className={clsx(
            "col-span-1 lg:col-span-2 xl:col-span-1",
            ((data?.stockAlerts?.lowStockCount !== undefined ? data.stockAlerts.lowStockCount : (overview?.lowStockProductCount || 0)) + (data?.stockAlerts?.outOfStockCount || 0)) > 0 && "border-orange-200 bg-orange-50/10 shadow-orange-50/5"
          )}
          iconClassName={clsx(
            ((data?.stockAlerts?.lowStockCount !== undefined ? data.stockAlerts.lowStockCount : (overview?.lowStockProductCount || 0)) + (data?.stockAlerts?.outOfStockCount || 0)) > 0 
              ? "bg-orange-50 text-orange-600 border border-orange-100" 
              : "bg-gray-50 text-gray-600"
          )}
          valueClassName={clsx(((data?.stockAlerts?.lowStockCount !== undefined ? data.stockAlerts.lowStockCount : (overview?.lowStockProductCount || 0)) + (data?.stockAlerts?.outOfStockCount || 0)) > 0 && "text-orange-700")}
        >
          <div className="text-[11px] text-gray-400 mt-2 font-medium flex flex-wrap gap-x-2 gap-y-0.5">
            <span>Sắp hết: <span className="font-bold text-amber-600">{data?.stockAlerts?.lowStockCount !== undefined ? data.stockAlerts.lowStockCount : (overview?.lowStockProductCount || 0)}</span></span>
            <span>Hết hàng: <span className="font-bold text-rose-600">{data?.stockAlerts?.outOfStockCount || 0}</span></span>
          </div>
        </StatCard>

        {/* Sản phẩm đang bán */}
        <StatCard
          title="Sản phẩm đang bán"
          value={overview?.activeProductCount || 0}
          icon={Package}
          className="col-span-1 lg:col-span-2 xl:col-span-1"
          iconClassName="bg-indigo-50 text-indigo-600 border border-indigo-100"
        >
          <p className="text-[11px] text-gray-400 mt-2 font-medium">Sản phẩm trạng thái bán</p>
        </StatCard>
      </div>

      {/* 3. Charts Section */}
      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        
        {/* Doanh thu thực nhận theo ngày - Line Chart */}
        <div className="xl:col-span-2">
          <ChartCard 
            title="Biểu đồ doanh thu thực nhận theo ngày" 
            empty={dailyRevenueData.length === 0}
            emptyMessage="Chưa có doanh thu trong khoảng thời gian này."
          >
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={dailyRevenueData} margin={{ top: 10, right: 10, bottom: 5, left: 10 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F3F4F6" />
                <XAxis
                  dataKey="date"
                  tickFormatter={(val) => dayjs(val).format('DD/MM')}
                  stroke="#9CA3AF"
                  fontSize={11}
                  fontWeight={500}
                  tickLine={false}
                  axisLine={false}
                  tickMargin={10}
                />
                <YAxis
                  tickFormatter={(val) => `${val / 1000000}M`}
                  stroke="#9CA3AF"
                  fontSize={11}
                  fontWeight={500}
                  tickLine={false}
                  axisLine={false}
                  width={40}
                />
                <Tooltip content={<CustomRevenueTooltip />} />
                <Line
                  type="monotone"
                  dataKey="revenue"
                  stroke="#4F46E5"
                  strokeWidth={3}
                  dot={{ r: 4, strokeWidth: 2, fill: '#4F46E5' }}
                  activeDot={{ r: 6, strokeWidth: 2 }}
                />
              </LineChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

        {/* Donut Chart - Trạng thái trả hàng */}
        <div>
          <ChartCard 
            title="Trạng thái trả hàng"
            empty={returnChartData.length === 0}
            emptyMessage="Chưa có yêu cầu trả hàng trong thời gian này."
          >
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={returnChartData}
                  cx="50%"
                  cy="45%"
                  innerRadius={60}
                  outerRadius={85}
                  paddingAngle={3}
                  dataKey="value"
                  nameKey="name"
                >
                  {returnChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip
                  formatter={(value, name) => [`${value} yêu cầu`, name]}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #E5E7EB', boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.05)' }}
                />
                <Legend 
                  verticalAlign="bottom" 
                  height={36} 
                  iconType="circle" 
                  iconSize={8}
                  wrapperStyle={{ fontSize: '11px', fontWeight: 600, color: '#374151' }} 
                />
              </PieChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

        {/* Bar Chart - Phân bố trạng thái đơn hàng */}
        <div className="xl:col-span-3">
          <ChartCard 
            title="Phân bố trạng thái đơn hàng"
            empty={orderChartData.length === 0}
            emptyMessage="Chưa có đơn hàng nào được tạo trong thời gian này."
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={orderChartData} margin={{ top: 15, right: 10, bottom: 5, left: 10 }}>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#F3F4F6" />
                <XAxis 
                  dataKey="name" 
                  stroke="#9CA3AF"
                  fontSize={11}
                  fontWeight={600}
                  tickLine={false}
                  axisLine={false}
                  tickMargin={10}
                />
                <YAxis 
                  stroke="#9CA3AF"
                  fontSize={11}
                  fontWeight={500}
                  tickLine={false}
                  axisLine={false}
                  width={30}
                />
                <Tooltip 
                  formatter={(value) => [`${value} đơn hàng`, 'Số lượng']}
                  contentStyle={{ borderRadius: '12px', border: '1px solid #E5E7EB' }}
                />
                <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={50}>
                  {orderChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </ChartCard>
        </div>

      </div>

      {/* 4. Tables Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        {/* Top 5 sản phẩm bán chạy */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
          <div className="p-5 border-b border-gray-150 flex justify-between items-center">
            <h2 className="text-base font-bold text-gray-900 tracking-tight">Top 5 sản phẩm bán chạy trong khoảng thời gian</h2>
            <Link to="/admin/products" className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors flex items-center gap-1">
              <span>Quản lý sản phẩm</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="flex-1 overflow-x-auto">
            {topSellingProductsList.length > 0 ? (
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50/50 text-gray-400 border-b border-gray-100">
                  <tr>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider w-12">#</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Sản phẩm</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Đã bán</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-right">Doanh thu</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {topSellingProductsList.map((item, idx) => (
                    <tr key={item.productId || idx} className="hover:bg-gray-50/50 transition-colors group">
                      <td className="px-5 py-3.5 font-bold text-gray-400 group-hover:text-gray-900 transition-colors">{idx + 1}</td>
                      <td className="px-5 py-3.5">
                        <Link 
                          to={`/admin/products/form?id=${item.productId}`} 
                          className="font-bold text-gray-800 hover:text-indigo-600 transition-colors line-clamp-1"
                        >
                          {item.productName}
                        </Link>
                        <span className="text-[10px] text-gray-400 font-semibold block">Mã SP: #{item.productId}</span>
                      </td>
                      <td className="px-5 py-3.5 text-center font-bold text-gray-700">{item.quantity}</td>
                      <td className="px-5 py-3.5 text-right font-black text-indigo-600">{formatPrice(item.revenue || 0)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-sm font-medium text-gray-400 flex flex-col items-center gap-2">
                <Package className="w-7 h-7 text-gray-300" />
                <span>Chưa có sản phẩm bán trong khoảng thời gian này.</span>
              </div>
            )}
          </div>
        </div>

        {/* Bảng Yêu cầu trả hàng cần xử lý */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
          <div className="p-5 border-b border-gray-150 flex justify-between items-center">
            <h2 className="text-base font-bold text-gray-900 tracking-tight">Yêu cầu trả hàng cần xử lý</h2>
            <Link to="/staff/returns" className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors flex items-center gap-1">
              <span>Tất cả yêu cầu</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </Link>
          </div>
          <div className="flex-1 overflow-x-auto">
            {returnsQueueList.length > 0 ? (
              <table className="w-full text-left text-sm">
                <thead className="bg-gray-50/50 text-gray-400 border-b border-gray-100">
                  <tr>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Mã YC</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Đơn hàng</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Khách hàng</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Loại</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Trạng thái</th>
                    <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {returnsQueueList.map((item) => {
                    const statusStyle = RETURN_STATUS_MAP[item.status] || { label: item.status, badgeClass: 'bg-gray-50 text-gray-700 border-gray-200' };
                    return (
                      <tr key={item.returnId} className="hover:bg-gray-50/50 transition-colors">
                        <td className="px-5 py-3.5 font-bold text-gray-900">#{item.returnId}</td>
                        <td className="px-5 py-3.5 font-semibold text-gray-600">#{item.orderId}</td>
                        <td className="px-5 py-3.5 text-gray-700 font-medium">{item.customerName}</td>
                        <td className="px-5 py-3.5">
                          <span className="text-[11px] font-bold text-gray-600">
                            {item.requestTypeLabel}
                          </span>
                        </td>
                        <td className="px-5 py-3.5">
                          <span className={clsx(
                            "px-2.5 py-0.5 rounded-full text-[10px] font-bold border",
                            statusStyle.badgeClass
                          )}>
                            {statusStyle.label}
                          </span>
                        </td>
                        <td className="px-5 py-3.5 text-right">
                          <Link
                            to={`/staff/returns?returnId=${item.returnId}`}
                            className="inline-flex items-center gap-1 text-xs font-bold text-indigo-600 hover:text-indigo-800 transition-colors bg-indigo-50/50 hover:bg-indigo-50 px-2.5 py-1.5 rounded-lg border border-indigo-100/50"
                          >
                            <Eye className="w-3.5 h-3.5" />
                            <span>Xem</span>
                          </Link>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <div className="p-8 text-center text-sm font-medium text-gray-400 flex flex-col items-center gap-2">
                <CheckCircle className="w-7 h-7 text-emerald-400" />
                <span>Tuyệt vời! Không có yêu cầu trả hàng nào cần xử lý.</span>
              </div>
            )}
          </div>
        </div>

      </div>

      {/* 5. Cảnh báo tồn kho biến thể */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
        <div className="p-5 border-b border-gray-150 flex justify-between items-center">
          <div className="flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-orange-500" />
            <h2 className="text-base font-bold text-gray-900 tracking-tight">Biến thể sắp hết hàng & hết hàng</h2>
          </div>
          <Link to="/admin/products" className="text-xs font-semibold text-indigo-600 hover:text-indigo-800 transition-colors flex items-center gap-1">
            <span>Tất cả sản phẩm</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>
        <div className="overflow-x-auto">
          {((data?.stockAlerts?.lowStockItems || []).length > 0 || (data?.stockAlerts?.outOfStockItems || []).length > 0) ? (
            <table className="w-full text-left text-sm">
              <thead className="bg-gray-50/50 text-gray-400 border-b border-gray-100">
                <tr>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider">Sản phẩm</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Màu sắc</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Kích thước</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Tồn kho</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Ngưỡng cảnh báo</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-center">Trạng thái</th>
                  <th className="px-5 py-3 font-bold text-[10px] uppercase tracking-wider text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {/* Out of Stock Items First */}
                {(data?.stockAlerts?.outOfStockItems || []).slice(0, 5).map((item, idx) => (
                  <tr key={`oos-${item.variantId || idx}`} className="hover:bg-rose-50/15 transition-colors group">
                    <td className="px-5 py-3.5">
                      <Link to={`/admin/products/form?id=${item.productId}`} className="font-bold text-gray-800 hover:text-indigo-600 transition-colors line-clamp-1">
                        {item.productName}
                      </Link>
                      <span className="text-[10px] text-gray-400 font-semibold block">Mã SP: #{item.productId}</span>
                    </td>
                    <td className="px-5 py-3.5 text-center font-medium text-gray-650">{item.colorName || '—'}</td>
                    <td className="px-5 py-3.5 text-center font-bold text-gray-600">{item.size}</td>
                    <td className="px-5 py-3.5 text-center font-black text-red-650">0</td>
                    <td className="px-5 py-3.5 text-center text-gray-450 font-semibold">{item.threshold || 10}</td>
                    <td className="px-5 py-3.5 text-center">
                      <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold border bg-red-50 text-red-700 border-red-200">
                        Hết hàng
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <Link
                        to={`/admin/products/form?id=${item.productId}`}
                        className="inline-flex items-center gap-1 text-xs font-bold text-indigo-600 hover:text-indigo-800 transition-colors bg-indigo-50/50 hover:bg-indigo-50 px-2.5 py-1.5 rounded-lg border border-indigo-100/50"
                      >
                        <Edit className="w-3.5 h-3.5" />
                        <span>Nhập hàng</span>
                      </Link>
                    </td>
                  </tr>
                ))}
                {/* Low Stock Items Second */}
                {(data?.stockAlerts?.lowStockItems || []).slice(0, 5).map((item, idx) => (
                  <tr key={`low-${item.variantId || idx}`} className="hover:bg-amber-50/15 transition-colors group">
                    <td className="px-5 py-3.5">
                      <Link to={`/admin/products/form?id=${item.productId}`} className="font-bold text-gray-800 hover:text-indigo-600 transition-colors line-clamp-1">
                        {item.productName}
                      </Link>
                      <span className="text-[10px] text-gray-400 font-semibold block">Mã SP: #{item.productId}</span>
                    </td>
                    <td className="px-5 py-3.5 text-center font-medium text-gray-650">{item.colorName || '—'}</td>
                    <td className="px-5 py-3.5 text-center font-bold text-gray-600">{item.size}</td>
                    <td className="px-5 py-3.5 text-center font-black text-amber-600">{item.stockQuantity}</td>
                    <td className="px-5 py-3.5 text-center text-gray-450 font-semibold">{item.threshold || 10}</td>
                    <td className="px-5 py-3.5 text-center">
                      <span className="px-2.5 py-0.5 rounded-full text-[10px] font-bold border bg-amber-50 text-amber-700 border-amber-200">
                        Sắp hết hàng
                      </span>
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <Link
                        to={`/admin/products/form?id=${item.productId}`}
                        className="inline-flex items-center gap-1 text-xs font-bold text-indigo-600 hover:text-indigo-800 transition-colors bg-indigo-50/50 hover:bg-indigo-50 px-2.5 py-1.5 rounded-lg border border-indigo-100/50"
                      >
                        <Edit className="w-3.5 h-3.5" />
                        <span>Nhập hàng</span>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div className="p-8 text-center text-sm font-medium text-gray-400 flex flex-col items-center gap-2">
              <CheckCircle className="w-7 h-7 text-emerald-400" />
              <span>Tuyệt vời! Tất cả biến thể sản phẩm đều có số lượng tồn kho dồi dào.</span>
            </div>
          )}
        </div>
      </div>

    </div>
  );
};

export default DashboardPage;
