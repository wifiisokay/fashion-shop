import axiosInstance from './axiosInstance';

export const categoryApi = {
  // === PUBLIC ===
  getTree: () => axiosInstance.get('/api/categories'),
  getById: (id) => axiosInstance.get(`/api/categories/${id}`),

  // === ADMIN ===
  create: (data) => axiosInstance.post('/api/admin/categories', data),
  update: (id, data) => axiosInstance.put(`/api/admin/categories/${id}`, data),
  delete: (id) => axiosInstance.delete(`/api/admin/categories/${id}`),
};
