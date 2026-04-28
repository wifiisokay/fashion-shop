import axiosInstance from './axiosInstance';

export const orderApi = {
  // Customer
  createOrder:    (data)       => axiosInstance.post('/api/orders', data),
  getMyOrders:    (params)     => axiosInstance.get('/api/orders', { params }),
  getMyOrderById: (id)         => axiosInstance.get(`/api/orders/${id}`),
  cancelOrder:      (id, reason) => axiosInstance.post(`/api/orders/${id}/cancel`, { reason }),
  confirmReceived:  (id)         => axiosInstance.post(`/api/orders/${id}/confirm-received`),

  // Staff + Admin
  getAllOrders:    (params)     => axiosInstance.get('/api/staff/orders', { params }),
  getOrderById:   (id)         => axiosInstance.get(`/api/staff/orders/${id}`),
  updateStatus:   (id, data)   => axiosInstance.patch(`/api/staff/orders/${id}/status`, data),
  confirmPacking: (id, data)   => axiosInstance.patch(`/api/staff/orders/${id}/packing`, data),
  staffCancel:    (id, reason) => axiosInstance.post(`/api/staff/orders/${id}/cancel`, { reason }),
};
