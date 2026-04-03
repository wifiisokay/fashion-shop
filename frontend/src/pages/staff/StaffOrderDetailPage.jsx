import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useStaffOrder } from '../../hooks/useStaffOrder';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, formatDate, formatOrderStatus } from '../../utils/format';
import { ArrowLeft, Package, Truck, CheckCircle, XCircle, Clock } from 'lucide-react';
import { clsx } from 'clsx';

const MOCK_ORDER = {
  id: 101,
  customerName: 'Nguyễn Văn A',
  customerPhone: '0901234567',
  shippingAddress: '123 Đường Lê Lợi, Phường Bến Thành, Quận 1, TP.HCM',
  status: 'PENDING',
  totalAmount: 1250000,
  paymentMethod: 'COD',
  paymentStatus: 'UNPAID',
  createdAt: '2026-03-30T10:00:00Z',
  items: [
    { id: 1, productId: 1, productName: 'Áo Thun Basic Nam', price: 250000, quantity: 2, imageUrl: 'https://picsum.photos/seed/1/100/100', size: 'M', color: 'Trắng' },
    { id: 2, productId: 2, productName: 'Quần Jean Ống Rộng', price: 750000, quantity: 1, imageUrl: 'https://picsum.photos/seed/2/100/100', size: 'L', color: 'Xanh' },
  ]
};

const STATUS_STEPS = ['PENDING', 'PROCESSING', 'SHIPPED', 'DELIVERED'];

const StaffOrderDetailPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { data, isLoading, isError, updateStatus, isUpdating } = useStaffOrder(id);
  const [localStatus, setLocalStatus] = useState('');

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError && !data) return <div className="text-center py-20 text-red-500">Lỗi tải chi tiết đơn hàng</div>;

  const order = data || { ...MOCK_ORDER, id: parseInt(id) || MOCK_ORDER.id };
  const currentStatus = localStatus || order.status;

  const handleUpdateStatus = async (newStatus) => {
    try {
      // In a real app, this would await the mutation
      // await updateStatus(newStatus);
      setLocalStatus(newStatus);
      alert(`Đã cập nhật trạng thái thành: ${formatOrderStatus(newStatus).label}`);
    } catch (error) {
      console.error('Failed to update status', error);
      alert('Lỗi khi cập nhật trạng thái');
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case 'PENDING': return <Clock className="w-5 h-5" />;
      case 'PROCESSING': return <Package className="w-5 h-5" />;
      case 'SHIPPED': return <Truck className="w-5 h-5" />;
      case 'DELIVERED': return <CheckCircle className="w-5 h-5" />;
      case 'CANCELLED': return <XCircle className="w-5 h-5" />;
      default: return <Clock className="w-5 h-5" />;
    }
  };

  return (
    <div className="space-y-6 max-w-5xl mx-auto">
      <div className="flex items-center gap-4">
        <Button variant="ghost" className="p-2" onClick={() => navigate('/staff/orders')}>
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-2xl font-bold text-gray-900">Chi tiết đơn hàng #{order.id}</h1>
        <div className="ml-auto">
          <span className={clsx(
            'px-3 py-1 rounded-full text-sm font-medium flex items-center gap-2',
            currentStatus === 'PENDING' ? 'bg-yellow-100 text-yellow-800' :
            currentStatus === 'PROCESSING' ? 'bg-blue-100 text-blue-800' :
            currentStatus === 'SHIPPED' ? 'bg-indigo-100 text-indigo-800' :
            currentStatus === 'DELIVERED' ? 'bg-green-100 text-green-800' :
            'bg-red-100 text-red-800'
          )}>
            {getStatusIcon(currentStatus)}
            {formatOrderStatus(currentStatus).label}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Items */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="p-4 border-b border-gray-200 bg-gray-50">
              <h2 className="font-semibold text-gray-900">Sản phẩm ({order.items.length})</h2>
            </div>
            <div className="divide-y divide-gray-100">
              {order.items.map((item) => (
                <div key={item.id} className="p-4 flex gap-4">
                  <img src={item.imageUrl} alt={item.productName} className="w-20 h-20 object-cover rounded-lg bg-gray-100" />
                  <div className="flex-1">
                    <h3 className="font-medium text-gray-900">{item.productName}</h3>
                    <p className="text-sm text-gray-500 mt-1">Phân loại: {item.color}, Size {item.size}</p>
                    <div className="flex justify-between items-center mt-2">
                      <span className="text-sm text-gray-600">SL: {item.quantity}</span>
                      <span className="font-medium text-gray-900">{formatPrice(item.price * item.quantity)}</span>
                    </div>
                  </div>
                </div>
              ))}
            </div>
            <div className="p-4 bg-gray-50 border-t border-gray-200 flex justify-between items-center">
              <span className="font-medium text-gray-700">Tổng cộng:</span>
              <span className="text-xl font-bold text-red-600">{formatPrice(order.totalAmount)}</span>
            </div>
          </div>

          {/* Status Update Actions */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Cập nhật trạng thái</h2>
            {currentStatus === 'CANCELLED' || currentStatus === 'RETURNED' ? (
              <p className="text-gray-500">Đơn hàng đã bị hủy hoặc hoàn trả, không thể cập nhật trạng thái.</p>
            ) : currentStatus === 'DELIVERED' ? (
              <p className="text-green-600 font-medium flex items-center gap-2">
                <CheckCircle className="w-5 h-5" /> Đơn hàng đã giao thành công.
              </p>
            ) : (
              <div className="flex flex-wrap gap-3">
                {currentStatus === 'PENDING' && (
                  <>
                    <Button onClick={() => handleUpdateStatus('PROCESSING')} className="bg-blue-600 hover:bg-blue-700">
                      Xác nhận & Chuẩn bị hàng
                    </Button>
                    <Button variant="outline" onClick={() => handleUpdateStatus('CANCELLED')} className="text-red-600 border-red-200 hover:bg-red-50">
                      Hủy đơn
                    </Button>
                  </>
                )}
                {currentStatus === 'PROCESSING' && (
                  <Button onClick={() => handleUpdateStatus('SHIPPED')} className="bg-indigo-600 hover:bg-indigo-700">
                    Bắt đầu giao hàng
                  </Button>
                )}
                {currentStatus === 'SHIPPED' && (
                  <Button onClick={() => handleUpdateStatus('DELIVERED')} className="bg-green-600 hover:bg-green-700">
                    Đã giao thành công
                  </Button>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Customer Info */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Thông tin khách hàng</h2>
            <div className="space-y-3 text-sm">
              <div>
                <span className="text-gray-500 block mb-1">Họ tên</span>
                <span className="font-medium text-gray-900">{order.customerName}</span>
              </div>
              <div>
                <span className="text-gray-500 block mb-1">Số điện thoại</span>
                <span className="font-medium text-gray-900">{order.customerPhone}</span>
              </div>
              <div>
                <span className="text-gray-500 block mb-1">Địa chỉ giao hàng</span>
                <span className="font-medium text-gray-900 leading-relaxed">{order.shippingAddress}</span>
              </div>
            </div>
          </div>

          {/* Payment Info */}
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
            <h2 className="font-semibold text-gray-900 mb-4">Thanh toán</h2>
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500">Phương thức</span>
                <span className="font-medium text-gray-900">{order.paymentMethod}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Trạng thái</span>
                <span className={clsx(
                  'font-medium',
                  order.paymentStatus === 'PAID' ? 'text-green-600' : 'text-yellow-600'
                )}>
                  {order.paymentStatus === 'PAID' ? 'Đã thanh toán' : 'Chưa thanh toán'}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500">Ngày đặt</span>
                <span className="font-medium text-gray-900">{formatDate(order.createdAt)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default StaffOrderDetailPage;
