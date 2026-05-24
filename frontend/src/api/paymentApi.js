import axiosInstance from './axiosInstance';

export const paymentApi = {
  // Customer — xem payment của đơn mình (cần auth)
  getPaymentByOrder: (orderId) => axiosInstance.get(`/api/payment/orders/${orderId}`),

  // Public — chỉ trả status, không cần auth (dùng cho PaymentResultPage sau redirect VNPay)
  getPaymentStatus: (orderId) => axiosInstance.get(`/api/payment/status/${orderId}`),

  // Admin — quản lý giao dịch
  getAllPayments:  (params) => axiosInstance.get('/api/admin/payments', { params }),
  getPaymentById: (id)     => axiosInstance.get(`/api/admin/payments/${id}`),
  refundPayment:  (id, data) => axiosInstance.post(`/api/admin/payments/${id}/refund`, data),
};
