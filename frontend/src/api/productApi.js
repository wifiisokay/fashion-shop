import axiosInstance from './axiosInstance';

export const productApi = {
  getAll: (params) => axiosInstance.get('/api/products', { params }),
  getById: (id) => axiosInstance.get(`/api/products/${id}`),
  getOutfitSuggestions: (id) => axiosInstance.get(`/api/products/${id}/outfits`),
};
