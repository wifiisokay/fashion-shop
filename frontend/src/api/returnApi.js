import axiosInstance from './axiosInstance';

export const returnApi = {
  // Customer
  createReturn: (data) => axiosInstance.post('/api/returns', data),
  getMyReturns:    (params) => axiosInstance.get('/api/returns/my', { params }),
  getMyReturnById: (id)     => axiosInstance.get(`/api/returns/${id}`),
  uploadEvidence: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axiosInstance.post('/api/returns/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  // Staff
  getAll:            (params) => axiosInstance.get('/api/staff/returns', { params }),
  getById:           (id)     => axiosInstance.get(`/api/staff/returns/${id}`),
  getStaffDashboard: () => axiosInstance.get('/api/staff/returns/dashboard'),
  approveReturn:     (id, data) => axiosInstance.patch(`/api/staff/returns/${id}/approve`, data),
  rejectReturn:      (id, data) => axiosInstance.patch(`/api/staff/returns/${id}/reject`, data),

  // Admin
  getAdminDashboard: () => axiosInstance.get('/api/admin/returns/dashboard'),
  getAdminAll:       (params) => axiosInstance.get('/api/admin/returns', { params }),
  getAdminById:      (id) => axiosInstance.get(`/api/admin/returns/${id}`),
  adminApprove:      (id, data) => axiosInstance.put(`/api/admin/returns/${id}/approve`, data),
  adminReject:       (id, data) => axiosInstance.put(`/api/admin/returns/${id}/reject`, data),
  markReceived:      (id, data) => axiosInstance.put(`/api/admin/returns/${id}/received`, data || {}),
  completeReturn:    (id, data) => axiosInstance.put(`/api/admin/returns/${id}/completed`, data || {}),
};
