import axiosInstance from './axiosInstance';

export const orderApi = {
  // Customer
  getMyOrders:   (params) => axiosInstance.get('/api/orders/my', { params }),
  getMyOrderById:(id)     => axiosInstance.get(`/api/orders/my/${id}`),
  cancelOrder:   (id, reason) => axiosInstance.patch(`/api/orders/${id}/cancel`, { reason }),
  requestReturn: (id, data)   => axiosInstance.post(`/api/orders/${id}/return`, data),

  // Staff + Admin
  getAllOrders:  (params) => axiosInstance.get('/api/staff/orders', { params }),
  getOrderById:  (id)     => axiosInstance.get(`/api/staff/orders/${id}`),
  updateStatus:  (id, status) => axiosInstance.patch(`/api/staff/orders/${id}/status`, { status }),
};
