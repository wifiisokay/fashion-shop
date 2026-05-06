import axiosInstance from './axiosInstance';

/**
 * API layer cho User Profile module.
 * Base: /api/user/profile
 *
 * Auth: tất cả endpoints đều yêu cầu HttpOnly cookie (tự động gửi qua withCredentials).
 */
export const userApi = {
  /**
   * GET /api/user/profile
   * Response.data.data: UserProfileResponse { userId, fullName, email, phone, role, avatarUrl, status, createdAt }
   */
  getProfile: () => axiosInstance.get('/api/user/profile'),

  /**
   * PUT /api/user/profile
   * Body: { fullName: string, phone?: string }
   * Response.data.data: UserProfileResponse
   */
  updateProfile: (data) => axiosInstance.put('/api/user/profile', data),

  /**
   * POST /api/user/profile/avatar
   * Body: FormData { file: File } — multipart/form-data
   * Response.data.data: UserProfileResponse với avatarUrl mới từ Cloudinary
   * Constraints: MIME whitelist jpeg/png/webp/gif, max 5MB
   */
  uploadAvatar: (file) => {
    const formData = new FormData();
    formData.append('file', file);
    return axiosInstance.post('/api/user/profile/avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  /**
   * DELETE /api/user/profile/avatar
   * Xóa avatar khỏi Cloudinary, set avatarUrl = null trong DB.
   * Response.data.data: UserProfileResponse với avatarUrl = null
   */
  removeAvatar: () => axiosInstance.delete('/api/user/profile/avatar'),

  // ==========================================
  // ADMIN API
  // ==========================================

  getAdminUsers: (params) => axiosInstance.get('/api/admin/users', { params }),
  
  getUserStats: () => axiosInstance.get('/api/admin/users/stats'),
  
  toggleUserStatus: (id, status) => axiosInstance.put(`/api/admin/users/${id}/status`, null, { params: { status } }),
};
