import axiosInstance from './axiosInstance';

export const shopConfigApi = {
  /** GET /api/admin/shop-config */
  getConfig: () => axiosInstance.get('/api/admin/shop-config'),

  /** PUT /api/admin/shop-config */
  updateConfig: (data) => axiosInstance.put('/api/admin/shop-config', data),
};
