import axiosInstance from './axiosInstance';

export const authApi = {
  login: (data) => axiosInstance.post('/api/auth/login', data),
  register: (data) => axiosInstance.post('/api/auth/register', data),
  me: () => axiosInstance.get('/api/auth/me'),
  logout: (data) => axiosInstance.post('/api/auth/logout', data),
  changePassword: (data) => axiosInstance.patch('/api/auth/change-password', data),
  forgotPassword: (data) => axiosInstance.post('/api/auth/forgot-password', data),
  resetPassword: (data) => axiosInstance.post('/api/auth/reset-password', data),
};
