import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatDate, formatPrice, parseReturnReason } from '../../utils/format';
import { useAuth } from '../../contexts/AuthContext';
import { clsx } from 'clsx';
import { Eye, Check, X as XIcon, Package, CheckCircle, AlertCircle, RotateCcw, DollarSign } from 'lucide-react';
import { toast } from 'sonner';


const STATUS_LABELS = {
  REQUESTED: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  RECEIVED: 'Đã nhận hàng trả',
  REFUNDED: 'Đã hoàn tiền',
  COMPLETED: 'Đã hoàn tiền'
};

const STATUS_OPTIONS = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'REQUESTED', label: 'Chờ duyệt' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Đã từ chối' },
  { value: 'RECEIVED', label: 'Đã nhận hàng trả' },
  { value: 'REFUNDED', label: 'Đã hoàn tiền' },
  { value: 'COMPLETED', label: 'Đã hoàn tiền (Cũ)' },
];

const STATUS_BADGE = {
  REQUESTED: { label: 'Chờ duyệt',    color: 'bg-yellow-100 text-yellow-800' },
  APPROVED:  { label: 'Đã duyệt',     color: 'bg-blue-100 text-blue-800' },
  REJECTED:  { label: 'Đã từ chối',       color: 'bg-red-100 text-red-800' },
  RECEIVED:  { label: 'Đã nhận hàng trả',  color: 'bg-purple-100 text-purple-800' },
  REFUNDED:  { label: 'Đã hoàn tiền',  color: 'bg-green-100 text-green-800' },
  COMPLETED: { label: 'Đã hoàn tiền',  color: 'bg-green-100 text-green-800' },
};

const SummaryCard = ({ title, value, icon: Icon }) => (
  <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm flex items-center justify-between">
    <div>
      <p className="text-xs font-medium text-gray-500 mb-1">{title}</p>
      <p className="text-xl font-bold text-gray-900">{value}</p>
    </div>
    <div className="w-10 h-10 rounded-full bg-gray-50 flex items-center justify-center text-gray-700">
      <Icon className="w-5 h-5" />
    </div>
  </div>
);

const StaffReturnManagePage = () => {
  const { user } = useAuth();
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [selectedReturn, setSelectedReturn] = useState(null);
  const [rejectNote, setRejectNote] = useState('');
  const [showRejectDialog, setShowRejectDialog] = useState(null);
  const [completeNote, setCompleteNote] = useState('');
  const [showCompleteDialog, setShowCompleteDialog] = useState(null);
  const [showApproveDialog, setShowApproveDialog] = useState(null);
  const [showReceiveDialog, setShowReceiveDialog] = useState(null);
  const queryClient = useQueryClient();
  const isAdmin = user?.role === 'ADMIN';

  const dashboardQuery = useQuery({
    queryKey: ['returnDashboard', isAdmin ? 'admin' : 'staff'],
    queryFn: () => (isAdmin ? returnApi.getAdminDashboard() : returnApi.getStaffDashboard()).then(r => r.data.data),
  });

  const { data, isLoading, isError } = useQuery({
    queryKey: ['staffReturns', statusFilter, page],
    queryFn: () => {
      const params = { status: statusFilter || undefined, page, size: 10 };
      return (isAdmin ? returnApi.getAdminAll(params) : returnApi.getAll(params)).then(r => r.data.data);
    },
  });

  const detailQuery = useQuery({
    queryKey: ['staffReturnDetail', selectedReturn],
    queryFn: () => (isAdmin ? returnApi.getAdminById(selectedReturn) : returnApi.getById(selectedReturn)).then(r => r.data.data),
    enabled: !!selectedReturn,
  });

  const approveMutation = useMutation({
    mutationFn: (id) => (isAdmin ? returnApi.adminApprove(id, { note: '' }) : returnApi.approveReturn(id, { note: '' })),
    onSuccess: () => { toast.success('Đã duyệt yêu cầu'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); queryClient.invalidateQueries({ queryKey: ['returnDashboard'] }); setSelectedReturn(null); setShowApproveDialog(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi duyệt'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, note }) => (isAdmin ? returnApi.adminReject(id, { note }) : returnApi.rejectReturn(id, { note })),
    onSuccess: () => { toast.success('Đã từ chối yêu cầu'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); queryClient.invalidateQueries({ queryKey: ['returnDashboard'] }); setShowRejectDialog(null); setSelectedReturn(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi từ chối'),
  });

  const receiveMutation = useMutation({
    mutationFn: (id) => returnApi.markReceived(id, { note: '' }),
    onSuccess: () => { toast.success('Đã xác nhận nhận hàng'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); queryClient.invalidateQueries({ queryKey: ['returnDashboard'] }); setSelectedReturn(null); setShowReceiveDialog(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  const completeMutation = useMutation({
    mutationFn: ({ id, note }) => returnApi.completeReturn(id, { note: note || null }),
    onSuccess: () => { toast.success('Đã xác nhận hoàn tiền'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); queryClient.invalidateQueries({ queryKey: ['returnDashboard'] }); setShowCompleteDialog(null); setSelectedReturn(null); setCompleteNote(''); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  const getPageNumbers = () => {
    if (!data) return [];
    const totalPages = data.totalPages;
    const pages = [];
    
    if (totalPages <= 5) {
      for (let i = 0; i < totalPages; i++) pages.push(i);
    } else {
      pages.push(0);
      
      let start = Math.max(1, page - 1);
      let end = Math.min(totalPages - 2, page + 1);
      
      if (page <= 2) {
        end = 3;
      }
      if (page >= totalPages - 3) {
        start = totalPages - 4;
      }
      
      if (start > 1) {
        pages.push('ellipsis-start');
      }
      
      for (let i = start; i <= end; i++) {
        pages.push(i);
      }
      
      if (end < totalPages - 2) {
        pages.push('ellipsis-end');
      }
      
      pages.push(totalPages - 1);
    }
    
    return pages;
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-mono text-sm">#{val}</span> },
    { title: 'Mã đơn', dataIndex: 'orderId', key: 'orderId', render: (val) => <span className="font-medium">#{val}</span> },
    { title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName' },
    { title: 'Lý do', dataIndex: 'reason', key: 'reason', render: (val) => parseReturnReason(val).cleanReason || '-' },
    { title: 'Hoàn tiền', dataIndex: 'refundAmount', key: 'refundAmount', render: (val) => formatPrice(val || 0) },
    { title: 'Hạn còn', dataIndex: 'remainingReturnDays', key: 'remainingReturnDays', render: (val) => `${val ?? 0} ngày` },
    {
      title: 'Trạng thái', dataIndex: 'status', key: 'status',
      render: (val) => {
        const info = STATUS_BADGE[val] || { label: val, color: 'bg-gray-100 text-gray-800' };
        return <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', info.color)}>{info.label}</span>;
      }
    },
    { title: 'Ngày tạo', dataIndex: 'createdAt', key: 'createdAt', render: (val) => formatDate(val) },
    {
      title: 'Hành động', dataIndex: 'id', key: 'action',
      render: (id) => (
        <Button variant="ghost" size="sm" onClick={() => setSelectedReturn(id)}>
          <Eye className="w-4 h-4 mr-1" /> Chi tiết
        </Button>
      )
    },
  ];

  if (isError) return <div className="text-center py-20 text-red-500">Không thể tải dữ liệu</div>;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý trả hàng</h1>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border"
        >
          {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {/* SLA Alerts Banner */}
      {dashboardQuery.data?.alerts && (dashboardQuery.data.alerts.approvedOver3Days > 0 || dashboardQuery.data.alerts.receivedNotCompleted > 0) && (
        <div className="bg-amber-50 border-l-4 border-amber-500 rounded-r-xl p-4 shadow-sm space-y-2">
          <div className="flex items-center gap-2 text-amber-800 font-semibold">
            <AlertCircle className="w-5 h-5 shrink-0" />
            <span>Cảnh báo SLA trả hàng cần lưu ý</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm text-amber-700 pl-7">
            {dashboardQuery.data.alerts.approvedOver3Days > 0 && (
              <div>
                ⚠️ <strong className="font-semibold">{dashboardQuery.data.alerts.approvedOver3Days}</strong> yêu cầu đã DUYỆT quá 3 ngày chưa nhận được hàng.
              </div>
            )}
            {dashboardQuery.data.alerts.receivedNotCompleted > 0 && (
              <div>
                ⚠️ <strong className="font-semibold">{dashboardQuery.data.alerts.receivedNotCompleted}</strong> yêu cầu đã NHẬN HÀNG quá hạn chưa hoàn tất xử lý.
              </div>
            )}
          </div>
        </div>
      )}

      {dashboardQuery.data?.summary && (
        <div className="space-y-4">
          {/* Primary Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
            <SummaryCard title="Chờ xử lý" value={dashboardQuery.data.summary.pending || 0} icon={RotateCcw} />
            <SummaryCard title="Quá 24h" value={dashboardQuery.data.summary.pendingOver24h || dashboardQuery.data.alerts?.pendingOver24h || 0} icon={AlertCircle} />
            <SummaryCard title="Đang xử lý" value={dashboardQuery.data.summary.processing || 0} icon={Package} />
            <SummaryCard title="Hoàn tất tháng" value={dashboardQuery.data.summary.completedThisMonth || 0} icon={CheckCircle} />
            {isAdmin && (
              <SummaryCard title="Tiền hoàn ghi nhận" value={formatPrice(dashboardQuery.data.summary.processedRefundAmountThisMonth || 0)} icon={DollarSign} />
            )}
          </div>
          
          {/* Secondary stats */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-4 bg-gray-50 p-4 rounded-xl border border-gray-200 shadow-sm">
            <div className="text-center sm:text-left">
              <span className="block text-xs font-medium text-gray-500 mb-0.5">Duyệt hôm nay</span>
              <span className="text-lg font-bold text-gray-900">{dashboardQuery.data.summary.approvedToday || 0}</span>
            </div>
            <div className="text-center sm:text-left border-l border-gray-200 pl-4">
              <span className="block text-xs font-medium text-gray-500 mb-0.5">Từ chối hôm nay</span>
              <span className="text-lg font-bold text-gray-900">{dashboardQuery.data.summary.rejectedToday || 0}</span>
            </div>
            <div className="text-center sm:text-left border-l border-gray-200 pl-4">
              <span className="block text-xs font-medium text-gray-500 mb-0.5">Từ chối tháng này</span>
              <span className="text-lg font-bold text-gray-900">{dashboardQuery.data.summary.rejectedThisMonth || 0}</span>
            </div>
            <div className="text-center sm:text-left border-l border-gray-200 pl-4">
              <span className="block text-xs font-medium text-gray-500 mb-0.5">SL hàng trả tháng</span>
              <span className="text-lg font-bold text-gray-900">{dashboardQuery.data.summary.returnItemQuantityThisMonth || 0} SP</span>
            </div>
            <div className="text-center sm:text-left border-l border-gray-200 pl-4">
              <span className="block text-xs font-medium text-gray-500 mb-0.5">Giá trị hàng trả tháng</span>
              <span className="text-lg font-bold text-gray-900">{formatPrice(dashboardQuery.data.summary.returnItemValueThisMonth || 0)}</span>
            </div>
          </div>
        </div>
      )}


      {/* Urgent Queue & Top Returned Products */}
      {dashboardQuery.data && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Urgent Queue */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
            <div className="p-4 border-b border-gray-150 bg-gray-50">
              <h2 className="text-sm font-bold text-gray-900">Hàng đợi xử lý ưu tiên</h2>
              <p className="text-xs text-gray-500">5 yêu cầu trả hàng cần được xem xét xử lý gấp nhất</p>
            </div>
            <div className="overflow-x-auto flex-1">
              {dashboardQuery.data.queue && dashboardQuery.data.queue.length > 0 ? (
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-100 text-gray-600 uppercase tracking-wider text-[10px]">
                    <tr>
                      <th className="px-4 py-3 font-semibold">Mã yêu cầu</th>
                      <th className="px-4 py-3 font-semibold">Khách hàng</th>
                      <th className="px-4 py-3 font-semibold text-right">Giá trị</th>
                      <th className="px-4 py-3 font-semibold text-center">Hành động</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {dashboardQuery.data.queue.map((req) => (
                      <tr key={req.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 font-mono font-medium text-gray-900">#{req.id}</td>
                        <td className="px-4 py-3 font-medium text-gray-700 truncate max-w-[120px]">{req.customerName}</td>
                        <td className="px-4 py-3 text-right font-medium text-red-600">{formatPrice(req.refundAmount || 0)}</td>
                        <td className="px-4 py-3 text-center">
                          <button
                            onClick={() => setSelectedReturn(req.id)}
                            className="text-blue-600 hover:text-blue-800 font-semibold text-[11px] underline"
                          >
                            Xử lý nhanh
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="p-6 text-center text-xs text-gray-500">Hàng đợi trống. Tuyệt vời!</div>
              )}
            </div>
          </div>

          {/* Top Returned Products */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
            <div className="p-4 border-b border-gray-150 bg-gray-50">
              <h2 className="text-sm font-bold text-gray-900">Sản phẩm hoàn trả nhiều nhất</h2>
              <p className="text-xs text-gray-500">Nhận diện các sản phẩm gặp nhiều phản ánh chất lượng</p>
            </div>
            <div className="overflow-x-auto flex-1">
              {dashboardQuery.data.topReturnedProducts && dashboardQuery.data.topReturnedProducts.length > 0 ? (
                <table className="w-full text-left text-xs">
                  <thead className="bg-gray-100 text-gray-600 uppercase tracking-wider text-[10px]">
                    <tr>
                      <th className="px-4 py-3 font-semibold">Tên sản phẩm</th>
                      <th className="px-4 py-3 font-semibold text-center">SL Trả</th>
                      <th className="px-4 py-3 font-semibold text-right">Giá trị trả</th>
                      <th className="px-4 py-3 font-semibold text-center">Số lượt yêu cầu</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {dashboardQuery.data.topReturnedProducts.map((prod, idx) => (
                      <tr key={idx} className="hover:bg-gray-50 transition-colors">
                        <td className="px-4 py-3 font-medium text-gray-900 truncate max-w-[180px]">{prod.productName}</td>
                        <td className="px-4 py-3 text-center font-semibold text-gray-700">{prod.returnedQuantity}</td>
                        <td className="px-4 py-3 text-right font-medium text-indigo-600">{formatPrice(prod.returnedValue || 0)}</td>
                        <td className="px-4 py-3 text-center text-gray-600 font-medium">{prod.requestCount} lượt</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <div className="p-6 text-center text-xs text-gray-500">Chưa có dữ liệu thống kê sản phẩm hoàn trả</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Main Request Table Header */}
      <div className="pt-4 border-t border-gray-105">
        <h2 className="text-sm font-bold text-gray-900 mb-3">Tất cả danh sách yêu cầu</h2>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <DataTable columns={columns} data={data?.content || []} loading={isLoading} emptyText="Không có yêu cầu trả hàng nào" />
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex justify-center items-center gap-2">
          <button
            disabled={page === 0}
            onClick={() => setPage(p => p - 1)}
            className="px-2.5 py-1.5 rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-50 text-sm font-medium transition-colors"
          >
            Trước
          </button>
          
          {getPageNumbers().map((p, idx) => {
            if (p === 'ellipsis-start' || p === 'ellipsis-end') {
              return <span key={`ellipsis-${idx}`} className="px-2 py-1 text-gray-400">...</span>;
            }
            return (
              <button
                key={p}
                onClick={() => setPage(p)}
                className={clsx(
                  'px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
                  p === page
                    ? 'bg-gray-900 text-white'
                    : 'bg-white border border-gray-300 text-gray-700 hover:bg-gray-50'
                )}
              >
                {p + 1}
              </button>
            );
          })}

          <button
            disabled={page >= data.totalPages - 1}
            onClick={() => setPage(p => p + 1)}
            className="px-2.5 py-1.5 rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 disabled:opacity-50 text-sm font-medium transition-colors"
          >
            Sau
          </button>
        </div>
      )}

      {/* Detail Modal */}
      {selectedReturn && detailQuery.data && (
        <div onClick={() => setSelectedReturn(null)} className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-900">Chi tiết yêu cầu trả hàng #{detailQuery.data.id}</h2>
              <button onClick={() => setSelectedReturn(null)} className="p-1.5 hover:bg-gray-100 rounded-full"><XIcon className="w-5 h-5" /></button>
            </div>

            <div className="p-6 space-y-5">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div><span className="text-gray-500">Mã đơn:</span> <Link to={`/staff/orders/${detailQuery.data.orderId}`} className="font-medium text-blue-600 hover:underline">#{detailQuery.data.orderId} ↗</Link></div>
                <div><span className="text-gray-500">Khách hàng:</span> <span className="font-medium">{detailQuery.data.customerName}</span></div>
                <div><span className="text-gray-500">Trạng thái:</span> <span className="font-semibold">{detailQuery.data.statusLabel}</span></div>
                <div><span className="text-gray-500">Ngày tạo:</span> <span className="font-medium">{formatDate(detailQuery.data.createdAt)}</span></div>
                {detailQuery.data.refundAmount && (
                  <div><span className="text-gray-500">Số tiền hoàn:</span> <span className="font-medium text-green-600">{formatPrice(detailQuery.data.refundAmount)}</span></div>
                )}
              </div>

              {detailQuery.data.items && detailQuery.data.items.length > 0 && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-3">Sản phẩm yêu cầu trả</h3>
                  <div className="border border-gray-200 rounded-xl divide-y divide-gray-200 overflow-hidden bg-gray-50">
                    {detailQuery.data.items.map((item) => (
                      <div key={item.id} className="p-3 flex items-center gap-3">
                        <div className="w-12 h-12 shrink-0 rounded-lg overflow-hidden border border-gray-200 bg-white">
                          {item.imageUrl ? (
                            <img src={item.imageUrl} alt={item.productName} className="w-full h-full object-cover" />
                          ) : (
                            <div className="w-full h-full bg-gray-100 flex items-center justify-center text-xs text-gray-400">Ảnh</div>
                          )}
                        </div>
                        <div className="flex-1 min-w-0">
                          <h4 className="text-sm font-medium text-gray-900 truncate">{item.productName}</h4>
                          <p className="text-xs text-gray-500 mt-0.5">
                            Phân loại: <span className="font-medium text-gray-700">{item.colorName || '-'}</span> | Size: <span className="font-medium text-gray-700">{item.size || '-'}</span>
                          </p>
                        </div>
                        <div className="text-right shrink-0">
                          <span className="text-xs text-gray-500 block">Số lượng: <strong className="text-gray-900">{item.quantity}</strong></span>
                          <span className="text-sm font-bold text-red-600 block mt-0.5">{formatPrice(item.subtotal || 0)}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <div className="flex items-center gap-2 mb-2">
                  <h3 className="text-sm font-bold text-gray-900">Nội dung yêu cầu</h3>
                </div>
                <p className="text-sm text-gray-700 bg-gray-50 rounded-lg p-3 whitespace-pre-line">
                  {parseReturnReason(detailQuery.data.reason).cleanReason}
                </p>
              </div>

              {detailQuery.data.adminNote && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-2">Ghi chú xử lý</h3>
                  <p className="text-sm text-gray-700 bg-yellow-50 rounded-lg p-3 whitespace-pre-line">{detailQuery.data.adminNote}</p>
                </div>
              )}

              {/* Evidence images */}
              {detailQuery.data.evidenceImages?.length > 0 && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-2">Ảnh minh chứng</h3>
                  <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
                    {detailQuery.data.evidenceImages.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                        className="aspect-square rounded-lg overflow-hidden border border-gray-200 hover:border-gray-400 transition-colors">
                        <img src={url} alt="" className="w-full h-full object-cover" referrerPolicy="no-referrer" />
                      </a>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {/* Actions */}
            <div className="flex gap-3 justify-end p-6 border-t border-gray-200">
              {detailQuery.data.status === 'REQUESTED' && (
                <>
                  <Button onClick={() => setShowApproveDialog(detailQuery.data.id)}
                    loading={approveMutation.isPending}>
                    <Check className="w-4 h-4 mr-1" /> Duyệt
                  </Button>
                  <Button variant="danger" onClick={() => setShowRejectDialog(detailQuery.data.id)}>
                    <XIcon className="w-4 h-4 mr-1" /> Từ chối
                  </Button>
                </>
              )}
              {detailQuery.data.status === 'APPROVED' && isAdmin && (
                <Button onClick={() => setShowReceiveDialog(detailQuery.data.id)}
                  loading={receiveMutation.isPending}>
                  <Package className="w-4 h-4 mr-1" /> Xác nhận đã nhận hàng
                </Button>
              )}
              {detailQuery.data.status === 'RECEIVED' && isAdmin && (
                <Button onClick={() => setShowCompleteDialog(detailQuery.data.id)}>
                  <CheckCircle className="w-4 h-4 mr-1" /> Xác nhận hoàn tiền
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Reject Dialog */}
      {showRejectDialog && (
        <div onClick={() => { setShowRejectDialog(null); setRejectNote(''); }} className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Từ chối yêu cầu</h3>
            <textarea
              value={rejectNote}
              onChange={(e) => setRejectNote(e.target.value)}
              placeholder="Lý do từ chối (bắt buộc)..."
              rows={3}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <div className="flex gap-3 justify-end">
              <Button variant="secondary" onClick={() => { setShowRejectDialog(null); setRejectNote(''); }}>Hủy</Button>
              <Button variant="danger"
                loading={rejectMutation.isPending}
                onClick={() => {
                  if (!rejectNote.trim()) { toast.error('Vui lòng nhập lý do'); return; }
                  rejectMutation.mutate({ id: showRejectDialog, note: rejectNote.trim() });
                }}>Từ chối</Button>
            </div>
          </div>
        </div>
      )}

      {/* Complete Dialog */}
      {showCompleteDialog && (
        <div onClick={() => { setShowCompleteDialog(null); setCompleteNote(''); }} className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Xác nhận hoàn tiền</h3>
            <textarea
              value={completeNote}
              onChange={(e) => setCompleteNote(e.target.value)}
              placeholder="Ghi chú (không bắt buộc)..."
              rows={3}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <div className="flex gap-3 justify-end">
              <Button variant="secondary" onClick={() => { setShowCompleteDialog(null); setCompleteNote(''); }}>Hủy</Button>
              <Button
                loading={completeMutation.isPending}
                onClick={() => {
                  completeMutation.mutate({
                    id: showCompleteDialog,
                    note: completeNote.trim() || null,
                  });
                }}>Xác nhận đã xử lý</Button>
            </div>
          </div>
        </div>
      )}

      {/* Custom Approve Dialog */}
      {showApproveDialog && (
        <div onClick={() => setShowApproveDialog(null)} className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Duyệt yêu cầu trả hàng</h3>
            <p className="text-sm text-gray-600">Bạn có chắc chắn muốn duyệt yêu cầu trả hàng này? Hành động này sẽ chuyển trạng thái yêu cầu sang <span className="font-semibold text-blue-700">ĐÃ DUYỆT</span>.</p>
            <div className="flex gap-3 justify-end pt-2">
              <Button variant="secondary" onClick={() => setShowApproveDialog(null)}>Hủy</Button>
              <Button
                loading={approveMutation.isPending}
                onClick={() => approveMutation.mutate(showApproveDialog)}
              >
                Duyệt yêu cầu
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Custom Receive Dialog */}
      {showReceiveDialog && (
        <div onClick={() => setShowReceiveDialog(null)} className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Xác nhận nhận hàng</h3>
            <p className="text-sm text-gray-600">Bạn có chắc chắn đã nhận được hàng trả về và tiến hành xử lý yêu cầu? Trạng thái yêu cầu sẽ chuyển sang <span className="font-semibold text-purple-700">ĐÃ NHẬN HÀNG</span>.</p>
            <div className="flex gap-3 justify-end pt-2">
              <Button variant="secondary" onClick={() => setShowReceiveDialog(null)}>Hủy</Button>
              <Button
                loading={receiveMutation.isPending}
                onClick={() => receiveMutation.mutate(showReceiveDialog)}
              >
                Xác nhận
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StaffReturnManagePage;
