import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatDate, formatPrice, parseReturnReason } from '../../utils/format';
import { Link } from 'react-router-dom';
import { clsx } from 'clsx';
import { Eye, X as XIcon, AlertCircle, RotateCcw, Package, CheckCircle, ArrowRight } from 'lucide-react';
import { ROUTES } from '../../constants/routes';

const STATUS_TABS = [
  { key: '', label: 'Tất cả' },
  { key: 'PENDING', label: 'Chờ xử lý' },
  { key: 'APPROVED', label: 'Đã duyệt' },
  { key: 'REJECTED', label: 'Từ chối' },
  { key: 'RECEIVED', label: 'Đã nhận hàng' },
  { key: 'COMPLETED', label: 'Hoàn tất' },
];

const STATUS_BADGE = {
  PENDING:   { label: 'Chờ xử lý',    color: 'bg-yellow-100 text-yellow-800' },
  APPROVED:  { label: 'Đã duyệt',     color: 'bg-blue-100 text-blue-800' },
  REJECTED:  { label: 'Từ chối',       color: 'bg-red-100 text-red-800' },
  RECEIVED:  { label: 'Đã nhận hàng',  color: 'bg-purple-100 text-purple-800' },
  COMPLETED: { label: 'Hoàn tất',      color: 'bg-green-100 text-green-800' },
};

const MyReturnsPage = () => {
  const [activeStatus, setActiveStatus] = useState('');
  const [page, setPage] = useState(0);
  const [selectedReturnId, setSelectedReturnId] = useState(null);

  const params = {
    ...(activeStatus && { status: activeStatus }),
    page,
    size: 5
  };

  const { data, isLoading, isError } = useQuery({
    queryKey: ['myReturns', activeStatus, page],
    queryFn: () => returnApi.getMyReturns(params).then(r => r.data.data),
  });

  const detailQuery = useQuery({
    queryKey: ['myReturnDetail', selectedReturnId],
    queryFn: () => returnApi.getMyReturnById(selectedReturnId).then(r => r.data.data),
    enabled: !!selectedReturnId,
  });

  const handleStatusChange = (status) => {
    setActiveStatus(status);
    setPage(0);
  };

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải danh sách yêu cầu đổi/trả</div>;

  const returns = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl sm:text-3xl font-bold text-gray-900 font-sans">Đổi/Trả & Khiếu nại của tôi</h1>
        <Link to={ROUTES.MY_ORDERS}>
          <Button variant="outline" size="sm">
            Tạo yêu cầu mới <ArrowRight className="w-4 h-4 ml-1.5" />
          </Button>
        </Link>
      </div>

      {/* Status tabs */}
      <div className="flex overflow-x-auto pb-2 gap-2 hide-scrollbar border-b border-gray-100">
        {STATUS_TABS.map(tab => (
          <button
            key={tab.key}
            onClick={() => handleStatusChange(tab.key)}
            className={clsx(
              'whitespace-nowrap px-4 py-2.5 text-sm font-medium border-b-2 transition-colors',
              activeStatus === tab.key
                ? 'border-black text-black'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            )}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {returns.length === 0 ? (
        <div className="text-center py-20 text-gray-500 bg-white rounded-2xl border border-gray-100 shadow-sm space-y-4">
          <p>Bạn không có yêu cầu đổi/trả hoặc khiếu nại nào ở trạng thái này.</p>
          {!activeStatus && <Link to={ROUTES.PRODUCTS}><Button>Mua sắm ngay</Button></Link>}
        </div>
      ) : (
        <div className="space-y-4">
          {returns.map(ret => {
            const statusInfo = STATUS_BADGE[ret.status] || { label: ret.status, color: 'bg-gray-100 text-gray-800' };
            const typeInfo = parseReturnReason(ret.reason);
            return (
              <div key={ret.id} className="bg-white rounded-2xl border border-gray-100 p-5 sm:p-6 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4 pb-4 border-b border-gray-100">
                  <div>
                    <p className="text-sm text-gray-500">Mã yêu cầu: <span className="font-mono font-bold text-gray-900">#{ret.id}</span></p>
                    <p className="text-xs text-gray-400 mt-1">{formatDate(ret.createdAt)}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-semibold', statusInfo.color)}>
                      {statusInfo.label}
                    </span>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-sm text-gray-600">
                  <div>
                    <span className="text-gray-400 block text-xs">Mã đơn hàng gốc</span>
                    <Link to={`/my-orders/${ret.orderId}`} className="font-medium text-blue-600 hover:underline">#{ret.orderId} ↗</Link>
                  </div>
                  <div>
                    <span className="text-gray-400 block text-xs">Loại yêu cầu</span>
                    <span className="font-semibold text-gray-900">{ret.requestTypeLabel || typeInfo.typeLabel}</span>
                  </div>
                  <div>
                    <span className="text-gray-400 block text-xs">Tổng giá trị sản phẩm</span>
                    <span className="font-bold text-red-600">{formatPrice(ret.totalReturnValue || 0)}</span>
                  </div>
                </div>

                <div className="flex justify-between items-center mt-5 pt-3 border-t border-gray-50">
                  <span className="text-xs text-gray-400">Số lượng sản phẩm: {ret.totalReturnQuantity || 0}</span>
                  <Button variant="outline" size="sm" onClick={() => setSelectedReturnId(ret.id)}>
                    <Eye className="w-4 h-4 mr-1.5" /> Chi tiết xử lý
                  </Button>
                </div>
              </div>
            );
          })}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-8">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-50 hover:bg-gray-50 text-sm font-medium transition-colors bg-white text-gray-700"
              >
                Trước
              </button>
              <span className="text-sm text-gray-600 font-medium">Trang {page + 1} / {totalPages}</span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="px-3 py-1.5 border rounded-lg disabled:opacity-50 hover:bg-gray-50 text-sm font-medium transition-colors bg-white text-gray-700"
              >
                Sau
              </button>
            </div>
          )}
        </div>
      )}

      {/* Customer Detail Modal */}
      {selectedReturnId && detailQuery.data && (
        <div onClick={() => setSelectedReturnId(null)} className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div onClick={(e) => e.stopPropagation()} className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between p-6 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-900">Chi tiết yêu cầu đổi/trả #{detailQuery.data.id}</h2>
              <button onClick={() => setSelectedReturnId(null)} className="p-1.5 hover:bg-gray-100 rounded-full"><XIcon className="w-5 h-5" /></button>
            </div>

            <div className="p-6 space-y-6">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span className="text-gray-400 block text-xs">Mã đơn hàng gốc:</span>
                  <Link to={`/my-orders/${detailQuery.data.orderId}`} className="font-semibold text-blue-600 hover:underline">#{detailQuery.data.orderId} ↗</Link>
                </div>
                <div>
                  <span className="text-gray-400 block text-xs">Trạng thái:</span>
                  <span className={clsx('font-bold',
                    detailQuery.data.status === 'COMPLETED' ? 'text-green-600' :
                    detailQuery.data.status === 'REJECTED' ? 'text-red-600' : 'text-amber-600'
                  )}>{detailQuery.data.statusLabel}</span>
                </div>
                <div>
                  <span className="text-gray-400 block text-xs">Ngày tạo:</span>
                  <span className="font-medium text-gray-900">{formatDate(detailQuery.data.createdAt)}</span>
                </div>
                {detailQuery.data.refundAmount && (
                  <div>
                    <span className="text-gray-400 block text-xs">Số tiền đã hoàn:</span>
                    <span className="font-bold text-green-600">{formatPrice(detailQuery.data.refundAmount)}</span>
                  </div>
                )}
              </div>

              <div>
                <div className="flex items-center gap-2 mb-2">
                  <h3 className="text-sm font-bold text-gray-900">Nội dung yêu cầu</h3>
                  <span className="px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-50 text-blue-700">
                    {detailQuery.data.requestTypeLabel || parseReturnReason(detailQuery.data.reason).typeLabel}
                  </span>
                </div>
                <p className="text-sm text-gray-700 bg-gray-50 rounded-lg p-3 whitespace-pre-line">
                  {parseReturnReason(detailQuery.data.reason).cleanReason}
                </p>
              </div>

              {detailQuery.data.orderItems?.length > 0 && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-2">Sản phẩm đổi/trả</h3>
                  <div className="border border-gray-200 rounded-lg divide-y divide-gray-100 overflow-hidden">
                    {detailQuery.data.orderItems.map((item) => (
                      <div key={item.id} className="flex items-center gap-3 p-3 text-sm">
                        <img
                          src={item.imageUrl || 'https://via.placeholder.com/48'}
                          alt={item.productName}
                          className="w-12 h-12 rounded object-cover border border-gray-200"
                          referrerPolicy="no-referrer"
                        />
                        <div className="min-w-0 flex-1">
                          <p className="font-medium text-gray-900 truncate">{item.productName}</p>
                          <p className="text-xs text-gray-500">{item.colorName || 'Không màu'} / {item.size || 'Không size'} x {item.quantity}</p>
                        </div>
                        <div className="font-medium text-gray-900">{formatPrice(item.subtotal || 0)}</div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Evidence images */}
              {detailQuery.data.evidenceImages?.length > 0 && (
                <div>
                  <h3 className="text-sm font-bold text-gray-900 mb-2">Ảnh bằng chứng</h3>
                  <div className="grid grid-cols-4 gap-3">
                    {detailQuery.data.evidenceImages.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer"
                        className="aspect-square rounded-lg overflow-hidden border border-gray-200 hover:border-gray-400 transition-colors">
                        <img src={url} alt="" className="w-full h-full object-cover" referrerPolicy="no-referrer" />
                      </a>
                    ))}
                  </div>
                </div>
              )}

              {detailQuery.data.adminNote && (
                <div className="bg-yellow-50 border border-yellow-100 p-4 rounded-xl space-y-2">
                  <h3 className="text-xs font-bold text-yellow-800 uppercase tracking-wider">Phản hồi từ cửa hàng</h3>
                  <p className="text-sm text-yellow-900 whitespace-pre-line">{detailQuery.data.adminNote}</p>
                </div>
              )}
            </div>

            <div className="p-6 border-t border-gray-200 bg-gray-50 flex justify-end">
              <Button onClick={() => setSelectedReturnId(null)}>Đóng</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MyReturnsPage;
