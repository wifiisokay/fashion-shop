import { clsx } from 'clsx';
import { formatOrderStatus } from '../../utils/format';

const colorMap = {
  default: 'bg-gray-100 text-gray-700',
  gold:    'bg-yellow-100 text-yellow-800',
  blue:    'bg-blue-100 text-blue-800',
  cyan:    'bg-cyan-100 text-cyan-800',
  green:   'bg-green-100 text-green-800',
  red:     'bg-red-100 text-red-800',
  orange:  'bg-orange-100 text-orange-800',
  purple:  'bg-purple-100 text-purple-800',
};

const OrderStatusBadge = ({ status, order }) => {
  let { label, color } = formatOrderStatus(status);

  if (order) {
    if (status === 'AWAITING_PAYMENT' && order.paymentMethod === 'VNPAY') {
      label = 'Chờ thanh toán VNPay';
    } else if (status === 'PENDING' && order.paymentMethod === 'VNPAY' && order.paymentStatus === 'PAID') {
      label = 'Đã thanh toán - chờ shop xác nhận';
    }
  }

  return (
    <span className={clsx('px-2.5 py-0.5 rounded-full text-xs font-medium', colorMap[color])}>
      {label}
    </span>
  );
};

export default OrderStatusBadge;
