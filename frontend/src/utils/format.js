import dayjs from 'dayjs';

export const formatPrice = (price) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);

export const formatDate = (dateStr) => dayjs(dateStr).format('DD/MM/YYYY HH:mm');

export const formatOrderStatus = (status) => ({
  AWAITING_PAYMENT: { label: 'Chờ thanh toán', color: 'gold' },
  PENDING: { label: 'Chờ xác nhận', color: 'gold' },
  CONFIRMED: { label: 'Đã xác nhận', color: 'blue' },
  SHIPPING: { label: 'Đang giao', color: 'cyan' },
  DELIVERED: { label: 'Đã giao', color: 'green' },
  COMPLETED: { label: 'Hoàn thành', color: 'green' },
  CANCELLED: { label: 'Đã hủy', color: 'red' },
  RETURN_REQUESTED: { label: 'Yêu cầu đổi/trả hoặc khiếu nại', color: 'orange' },
  RETURNING: { label: 'Đang xử lý đổi/trả', color: 'orange' },
  RETURNED: { label: 'Đã xử lý đổi/trả', color: 'purple' },
}[status] ?? { label: status, color: 'default' });

export const formatReturnType = (type) => ({
  RETURN: { label: 'Trả hàng', color: 'red' },
  EXCHANGE: { label: 'Đổi hàng', color: 'blue' },
  COMPLAINT: { label: 'Khiếu nại', color: 'gold' },
  REFUND: { label: 'Hoàn tiền', color: 'red' },
}[type] ?? { label: type, color: 'default' });

export const RETURN_REASON_TYPES = [
  { value: 'RETURN', label: 'Trả hàng', prefix: '[TRẢ HÀNG]' },
  { value: 'EXCHANGE', label: 'Đổi hàng', prefix: '[ĐỔI HÀNG]' },
  { value: 'COMPLAINT', label: 'Khiếu nại', prefix: '[KHIẾU NẠI]' },
];

export const parseReturnReason = (reason = '') => {
  const match = reason.match(/^\[(TRẢ HÀNG|ĐỔI HÀNG|KHIẾU NẠI)\]\s*(.*)$/i);
  if (!match) {
    return { type: 'RETURN', typeLabel: 'Trả hàng', cleanReason: reason };
  }

  const prefix = match[1].toUpperCase();
  const type = prefix === 'ĐỔI HÀNG' ? 'EXCHANGE' : prefix === 'KHIẾU NẠI' ? 'COMPLAINT' : 'RETURN';
  const info = formatReturnType(type);
  return { type, typeLabel: info.label, cleanReason: match[2] || '' };
};

export const isSaleActive = (saleStartDate, saleEndDate) => {
  if (!saleStartDate || !saleEndDate) return false;
  const now = dayjs();
  return now.isAfter(dayjs(saleStartDate)) && now.isBefore(dayjs(saleEndDate));
};
