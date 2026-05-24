import axiosInstance from './axiosInstance';

export const addressApi = {
  /** GET /api/user/addresses — Lấy danh sách địa chỉ (default đứng đầu) */
  getAll: () => axiosInstance.get('/api/user/addresses'),

  /** GET /api/user/addresses/{id} */
  getById: (id) => axiosInstance.get(`/api/user/addresses/${id}`),

  /**
   * POST /api/user/addresses
   * Body: { fullName, phone, province, provinceCode, district, districtCode, ward, wardCode, street, isDefault }
   */
  create: (data) => axiosInstance.post('/api/user/addresses', data),

  /**
   * PUT /api/user/addresses/{id}
   * Body: same as create
   */
  update: (id, data) => axiosInstance.put(`/api/user/addresses/${id}`, data),

  /** DELETE /api/user/addresses/{id} — Backend tự set default mới nếu xóa default */
  remove: (id) => axiosInstance.delete(`/api/user/addresses/${id}`),

  /** PATCH /api/user/addresses/{id}/default — Idempotent, unset cũ set mới */
  setDefault: (id) => axiosInstance.patch(`/api/user/addresses/${id}/default`),
};
