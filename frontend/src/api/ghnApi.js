import axiosInstance from './axiosInstance';

/**
 * GHN Master Data API — dùng chung cho Customer (AddressManager) và Admin (ShopSettings).
 * Endpoints: /api/ghn/* (public, không cần auth)
 */
export const ghnApi = {
  /** GET /api/ghn/provinces */
  getProvinces: () => axiosInstance.get('/api/ghn/provinces'),

  /** GET /api/ghn/districts?province_id=X */
  getDistricts: (provinceId) =>
    axiosInstance.get(`/api/ghn/districts?province_id=${provinceId}`),

  /** GET /api/ghn/wards?district_id=X */
  getWards: (districtId) =>
    axiosInstance.get(`/api/ghn/wards?district_id=${districtId}`),
};
