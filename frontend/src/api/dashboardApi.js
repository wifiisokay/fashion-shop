import axiosInstance from './axiosInstance';

const dashboardApi = {
  getStats: () => {
    return axiosInstance.get('/api/admin/dashboard/stats');
  },
  getAdminDashboard: (params) => {
    return axiosInstance.get('/api/admin/dashboard', { params });
  }
};

export default dashboardApi;
