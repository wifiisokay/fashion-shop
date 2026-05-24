import axiosInstance from './axiosInstance';

const dashboardApi = {
  getStats: () => {
    return axiosInstance.get('/api/admin/dashboard/stats');
  }
};

export default dashboardApi;
