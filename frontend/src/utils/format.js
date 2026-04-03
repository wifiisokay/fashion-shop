import dayjs from 'dayjs';

export const formatPrice = (price) =>
  new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(price);

export const formatDate = (dateStr) => dayjs(dateStr).format('DD/MM/YYYY HH:mm');

export const formatOrderStatus = (status) => ({
  PENDING_PAYMENT:  { label: 'Chờ thanh toán',   color: 'default' },
  PENDING:          { label: 'Chờ xác nhận',      color: 'gold'    },
  CONFIRMED:        { label: 'Đã xác nhận',        color: 'blue'    },
  SHIPPING:         { label: 'Đang giao',          color: 'cyan'    },
  DELIVERED:        { label: 'Đã giao',            color: 'green'   },
  COMPLETED:        { label: 'Hoàn thành',         color: 'green'   },
  CANCELLED:        { label: 'Đã hủy',             color: 'red'     },
  RETURN_REQUESTED: { label: 'Yêu cầu trả hàng',  color: 'orange'  },
  RETURNING:        { label: 'Đang trả hàng',      color: 'orange'  },
  RETURNED:         { label: 'Đã trả hàng',        color: 'purple'  },
}[status] ?? { label: status, color: 'default' });

export const formatReturnType = (type) => ({
  REFUND:   { label: 'Hoàn tiền', color: 'red'  },
  EXCHANGE: { label: 'Đổi hàng',  color: 'blue' },
}[type] ?? { label: type, color: 'default' });

export const isSaleActive = (saleStartDate, saleEndDate) => {
  if (!saleStartDate || !saleEndDate) return false;
  const now = dayjs();
  return now.isAfter(dayjs(saleStartDate)) && now.isBefore(dayjs(saleEndDate));
};
