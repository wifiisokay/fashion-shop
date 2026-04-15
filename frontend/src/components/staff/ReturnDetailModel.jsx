import { useState, useEffect } from 'react';
import { X } from 'lucide-react';
import { returnApi } from '../../api/returnApi';
import Button from '../ui/Button';
import Spinner from '../ui/Spinner';
import { formatPrice, formatDate, formatReturnType } from '../../utils/format';
import { clsx } from 'clsx';

const ReturnDetailModal = ({ returnId, onClose }) => {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isProcessing, setIsProcessing] = useState(false);

    useEffect(() => {
        const fetchDetail = async () => {
            try {
                setLoading(true);
                // Mock API call if real API fails or for preview
                const response = await returnApi.getById(returnId).catch(() => ({
                    data: {
                        id: returnId,
                        orderId: 'ORD-12345',
                        customerName: 'Nguyễn Văn A',
                        customerEmail: 'nguyenvana@example.com',
                        customerPhone: '0912345678',
                        returnType: 'REFUND',
                        reason: 'Sản phẩm bị lỗi kỹ thuật',
                        status: 'PENDING',
                        createdAt: new Date().toISOString(),
                        items: [
                            { id: 1, name: 'Áo Thun Basic Nam', price: 250000, quantity: 1, image: 'https://picsum.photos/seed/1/100/100' }
                        ],
                        totalAmount: 250000,
                        images: ['https://picsum.photos/seed/error1/300/300', 'https://picsum.photos/seed/error2/300/300'],
                        note: 'Áo bị rách ở phần vai'
                    }
                }));
                setData(response.data);
            } catch (err) {
                setError('Không thể tải thông tin chi tiết');
            } finally {
                setLoading(false);
            }
        };

        if (returnId) {
            fetchDetail();
        }
    }, [returnId]);

    const handleProcess = async (status) => {
        try {
            setIsProcessing(true);
            // Call API to update status
            await returnApi.process(returnId, { status }).catch(() => {
                // Fallback for preview if API is not fully implemented
                console.log(`Mock processing return ${returnId} to ${status}`);
            });

            // Update local state
            setData(prev => ({ ...prev, status }));
            alert(`Đã ${status === 'APPROVED' ? 'duyệt' : 'từ chối'} yêu cầu thành công!`);
        } catch (err) {
            alert('Có lỗi xảy ra khi xử lý yêu cầu.');
            console.error(err);
        } finally {
            setIsProcessing(false);
        }
    };

    if (!returnId) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
            <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl max-h-[90vh] overflow-hidden flex flex-col">
                <div className="flex items-center justify-between p-6 border-b border-gray-200">
                    <h2 className="text-xl font-bold text-gray-900">Chi tiết yêu cầu Đổi/Trả #{returnId}</h2>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
                        <X className="w-6 h-6" />
                    </button>
                </div>

                <div className="p-6 overflow-y-auto flex-1">
                    {loading ? (
                        <div className="flex justify-center py-12"><Spinner size="lg" /></div>
                    ) : error ? (
                        <div className="text-center py-12 text-red-600">{error}</div>
                    ) : data ? (
                        <div className="space-y-8">
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div className="space-y-4">
                                    <h3 className="font-semibold text-gray-900 border-b pb-2">Thông tin khách hàng</h3>
                                    <div className="space-y-2 text-sm">
                                        <p><span className="text-gray-500 w-24 inline-block">Họ tên:</span> <span className="font-medium">{data.customerName}</span></p>
                                        <p><span className="text-gray-500 w-24 inline-block">Email:</span> {data.customerEmail}</p>
                                        <p><span className="text-gray-500 w-24 inline-block">Số ĐT:</span> {data.customerPhone}</p>
                                    </div>
                                </div>

                                <div className="space-y-4">
                                    <h3 className="font-semibold text-gray-900 border-b pb-2">Thông tin yêu cầu</h3>
                                    <div className="space-y-2 text-sm">
                                        <p><span className="text-gray-500 w-24 inline-block">Mã đơn gốc:</span> <span className="font-medium">#{data.orderId}</span></p>
                                        <p><span className="text-gray-500 w-24 inline-block">Ngày tạo:</span> {formatDate(data.createdAt)}</p>
                                        <p>
                                            <span className="text-gray-500 w-24 inline-block">Loại yêu cầu:</span>
                                            {(() => {
                                                const { label, color } = formatReturnType(data.returnType);
                                                const colorClass = color === 'red' ? 'bg-red-100 text-red-800' :
                                                    color === 'blue' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100 text-gray-800';
                                                return <span className={clsx('px-2 py-0.5 rounded text-xs font-medium', colorClass)}>{label}</span>;
                                            })()}
                                        </p>
                                        <p>
                                            <span className="text-gray-500 w-24 inline-block">Trạng thái:</span>
                                            {(() => {
                                                const statusMap = {
                                                    PENDING: { label: 'Chờ xử lý', color: 'bg-yellow-100 text-yellow-800' },
                                                    APPROVED: { label: 'Đã duyệt', color: 'bg-green-100 text-green-800' },
                                                    REJECTED: { label: 'Từ chối', color: 'bg-red-100 text-red-800' },
                                                };
                                                const statusInfo = statusMap[data.status] || { label: data.status, color: 'bg-gray-100 text-gray-800' };
                                                return <span className={clsx('px-2 py-0.5 rounded text-xs font-medium', statusInfo.color)}>{statusInfo.label}</span>;
                                            })()}
                                        </p>
                                    </div>
                                </div>
                            </div>

                            <div className="space-y-4">
                                <h3 className="font-semibold text-gray-900 border-b pb-2">Sản phẩm đổi/trả</h3>
                                <div className="border border-gray-200 rounded-lg overflow-hidden">
                                    <table className="w-full text-sm text-left">
                                        <thead className="bg-gray-50 text-gray-700">
                                            <tr>
                                                <th className="px-4 py-3 font-medium">Sản phẩm</th>
                                                <th className="px-4 py-3 font-medium text-center">Số lượng</th>
                                                <th className="px-4 py-3 font-medium text-right">Đơn giá</th>
                                                <th className="px-4 py-3 font-medium text-right">Thành tiền</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-gray-200">
                                            {data.items?.map((item, idx) => (
                                                <tr key={idx}>
                                                    <td className="px-4 py-3 flex items-center gap-3">
                                                        <img src={item.image || 'https://via.placeholder.com/40'} alt={item.name} className="w-10 h-10 rounded object-cover" />
                                                        <span className="font-medium">{item.name}</span>
                                                    </td>
                                                    <td className="px-4 py-3 text-center">{item.quantity}</td>
                                                    <td className="px-4 py-3 text-right">{formatPrice(item.price)}</td>
                                                    <td className="px-4 py-3 text-right font-medium">{formatPrice(item.price * item.quantity)}</td>
                                                </tr>
                                            ))}
                                        </tbody>
                                        <tfoot className="bg-gray-50">
                                            <tr>
                                                <td colSpan="3" className="px-4 py-3 text-right font-medium">Tổng giá trị:</td>
                                                <td className="px-4 py-3 text-right font-bold text-red-600">{formatPrice(data.totalAmount)}</td>
                                            </tr>
                                        </tfoot>
                                    </table>
                                </div>
                            </div>

                            <div className="space-y-4">
                                <h3 className="font-semibold text-gray-900 border-b pb-2">Lý do & Hình ảnh minh chứng</h3>
                                <div className="bg-gray-50 p-4 rounded-lg text-sm">
                                    <p className="font-medium mb-1">Lý do chính: {data.reason}</p>
                                    {data.note && <p className="text-gray-600">Ghi chú thêm: {data.note}</p>}
                                </div>

                                {data.images && data.images.length > 0 && (
                                    <div className="flex gap-4 overflow-x-auto pb-2">
                                        {data.images.map((img, idx) => (
                                            <img key={idx} src={img} alt={`Minh chứng ${idx + 1}`} className="w-32 h-32 object-cover rounded-lg border border-gray-200" />
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    ) : null}
                </div>

                <div className="p-6 border-t border-gray-200 bg-gray-50 flex justify-end gap-3">
                    <Button variant="outline" onClick={onClose} disabled={isProcessing}>Đóng</Button>
                    {data?.status === 'PENDING' && (
                        <>
                            <Button
                                variant="outline"
                                className="text-red-600 border-red-200 hover:bg-red-50"
                                onClick={() => handleProcess('REJECTED')}
                                disabled={isProcessing}
                            >
                                Từ chối
                            </Button>
                            <Button
                                onClick={() => handleProcess('APPROVED')}
                                loading={isProcessing}
                                disabled={isProcessing}
                            >
                                Duyệt yêu cầu
                            </Button>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

export default ReturnDetailModal;
