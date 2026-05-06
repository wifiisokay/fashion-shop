import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import DataTable from '../../components/admin/DataTable';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatDate, formatPrice } from '../../utils/format';
import { clsx } from 'clsx';
import { Eye, Check, X as XIcon, Package, CheckCircle } from 'lucide-react';
import { toast } from 'sonner';

const STATUS_OPTIONS = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'APPROVED', label: 'Đã duyệt' },
  { value: 'REJECTED', label: 'Từ chối' },
  { value: 'RECEIVED', label: 'Đã nhận hàng' },
  { value: 'COMPLETED', label: 'Hoàn tất' },
];

const STATUS_BADGE = {
  PENDING:   { label: 'Chờ xử lý',    color: 'bg-yellow-100 text-yellow-800' },
  APPROVED:  { label: 'Đã duyệt',     color: 'bg-blue-100 text-blue-800' },
  REJECTED:  { label: 'Từ chối',       color: 'bg-red-100 text-red-800' },
  RECEIVED:  { label: 'Đã nhận hàng',  color: 'bg-purple-100 text-purple-800' },
  COMPLETED: { label: 'Hoàn tất',      color: 'bg-green-100 text-green-800' },
};

const StaffReturnManagePage = () => {
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(0);
  const [selectedReturn, setSelectedReturn] = useState(null);
  const [rejectNote, setRejectNote] = useState('');
  const [showRejectDialog, setShowRejectDialog] = useState(null);
  const [refundAmount, setRefundAmount] = useState('');
  const [showCompleteDialog, setShowCompleteDialog] = useState(null);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['staffReturns', statusFilter, page],
    queryFn: () => returnApi.getAll({ status: statusFilter || undefined, page, size: 10 }).then(r => r.data.data),
  });

  const detailQuery = useQuery({
    queryKey: ['staffReturnDetail', selectedReturn],
    queryFn: () => returnApi.getById(selectedReturn).then(r => r.data.data),
    enabled: !!selectedReturn,
  });

  const approveMutation = useMutation({
    mutationFn: (id) => returnApi.approveReturn(id, { note: '' }),
    onSuccess: () => { toast.success('Đã duyệt yêu cầu'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); setSelectedReturn(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi duyệt'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, note }) => returnApi.rejectReturn(id, { note }),
    onSuccess: () => { toast.success('Đã từ chối yêu cầu'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); setShowRejectDialog(null); setSelectedReturn(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi từ chối'),
  });

  const receiveMutation = useMutation({
    mutationFn: (id) => returnApi.receiveReturn(id),
    onSuccess: () => { toast.success('Đã xác nhận nhận hàng'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); setSelectedReturn(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  const completeMutation = useMutation({
    mutationFn: ({ id, refundAmount }) => returnApi.completeReturn(id, { refundAmount: refundAmount || null }),
    onSuccess: () => { toast.success('Đã hoàn tất trả hàng'); queryClient.invalidateQueries({ queryKey: ['staffReturns'] }); setShowCompleteDialog(null); setSelectedReturn(null); },
    onError: (err) => toast.error(err.response?.data?.message || 'Lỗi'),
  });

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-mono text-sm">#{val}</span> },
    { title: 'Mã đơn', dataIndex: 'orderId', key: 'orderId', render: (val) => <span className="font-medium">#{val}</span> },
    { title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName' },
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
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Trả hàng</h1>
        <select
          value={statusFilter}
          onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}
          className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2 border"
        >
          {STATUS_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <DataTable columns={columns} data={data?.content || []} loading={isLoading} emptyText="Không có yêu cầu trả hàng nào" />
      </div>

      {/* Pagination */}
      {data && data.totalPages > 1 && (
        <div className="flex justify-center gap-2">
          {Array.from({ length: data.totalPages }, (_, i) => (
            <button key={i} onClick={() => setPage(i)}
              className={clsx('px-3 py-1.5 rounded-lg text-sm font-medium transition-colors',
                i === page ? 'bg-gray-900 text-white' : 'bg-white border border-gray-300 text-gray-700 hover:bg-gray-50'
              )}>{i + 1}</button>
          ))}
        </div>
      )}

      {/* Detail Modal */}
      {selectedReturn && detailQuery.data && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
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
                {detailQuery.data.processedByName && (
                  <div><span className="text-gray-500">Xử lý bởi:</span> <span className="font-medium">{detailQuery.data.processedByName}</span></div>
                )}
              </div>

              <div>
                <h3 className="text-sm font-bold text-gray-900 mb-2">Lý do trả hàng</h3>
                <p className="text-sm text-gray-700 bg-gray-50 rounded-lg p-3 whitespace-pre-line">{detailQuery.data.reason}</p>
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
              {detailQuery.data.status === 'PENDING' && (
                <>
                  <Button onClick={() => { if (window.confirm('Duyệt yêu cầu này?')) approveMutation.mutate(detailQuery.data.id); }}
                    loading={approveMutation.isPending}>
                    <Check className="w-4 h-4 mr-1" /> Duyệt
                  </Button>
                  <Button variant="danger" onClick={() => setShowRejectDialog(detailQuery.data.id)}>
                    <XIcon className="w-4 h-4 mr-1" /> Từ chối
                  </Button>
                </>
              )}
              {detailQuery.data.status === 'APPROVED' && (
                <Button onClick={() => { if (window.confirm('Xác nhận đã nhận hàng trả lại?')) receiveMutation.mutate(detailQuery.data.id); }}
                  loading={receiveMutation.isPending}>
                  <Package className="w-4 h-4 mr-1" /> Xác nhận nhận hàng
                </Button>
              )}
              {detailQuery.data.status === 'RECEIVED' && (
                <Button onClick={() => setShowCompleteDialog(detailQuery.data.id)}>
                  <CheckCircle className="w-4 h-4 mr-1" /> Hoàn tất
                </Button>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Reject Dialog */}
      {showRejectDialog && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Từ chối yêu cầu trả hàng</h3>
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
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/40 p-4">
          <div className="bg-white rounded-2xl shadow-xl max-w-md w-full p-6 space-y-4">
            <h3 className="text-lg font-bold text-gray-900">Hoàn tất trả hàng</h3>
            <p className="text-sm text-gray-600">Nhập số tiền hoàn (chỉ áp dụng cho đơn VNPAY đã thanh toán). Để trống nếu là đơn COD.</p>
            <input
              type="number"
              value={refundAmount}
              onChange={(e) => setRefundAmount(e.target.value)}
              placeholder="Số tiền hoàn (VNĐ)"
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black"
            />
            <div className="flex gap-3 justify-end">
              <Button variant="secondary" onClick={() => { setShowCompleteDialog(null); setRefundAmount(''); }}>Hủy</Button>
              <Button
                loading={completeMutation.isPending}
                onClick={() => {
                  if (!window.confirm('Xác nhận hoàn tất trả hàng? Hành động này không thể hoàn tác.')) return;
                  completeMutation.mutate({ id: showCompleteDialog, refundAmount: refundAmount ? parseFloat(refundAmount) : null });
                }}>Hoàn tất + Hoàn tiền</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default StaffReturnManagePage;
