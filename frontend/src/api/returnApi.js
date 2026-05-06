import axiosInstance from './axiosInstance';

export const returnApi = {
  // Customer
  createReturn: (data) => axiosInstance.post('/api/returns', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }),
  getMyReturns:    (params) => axiosInstance.get('/api/returns/my', { params }),
  getMyReturnById: (id)     => axiosInstance.get(`/api/returns/my/${id}`),

  // Staff
  getAll:        (params) => axiosInstance.get('/api/staff/returns', { params }),
  getById:       (id)     => axiosInstance.get(`/api/staff/returns/${id}`),
  approveReturn: (id, data) => axiosInstance.patch(`/api/staff/returns/${id}/approve`, data),
  rejectReturn:  (id, data) => axiosInstance.patch(`/api/staff/returns/${id}/reject`, data),

  // Admin
  receiveReturn:  (id) => axiosInstance.patch(`/api/admin/returns/${id}/receive`),
  completeReturn: (id, data) => axiosInstance.patch(`/api/admin/returns/${id}/complete`, data),
};
