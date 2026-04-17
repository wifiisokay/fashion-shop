import axiosInstance from './axiosInstance';

export const productApi = {
  // === PUBLIC ===
  getAll: (params) => axiosInstance.get('/api/products', { params }),
  getById: (id) => axiosInstance.get(`/api/products/${id}`),
  getOutfitSuggestions: (id) => axiosInstance.get(`/api/products/${id}/outfits`),

  // === ADMIN PRODUCTS ===
  adminList: (params) => axiosInstance.get('/api/admin/products', { params }),
  adminGetById: (id) => axiosInstance.get(`/api/admin/products/${id}`),
  create: (data) => axiosInstance.post('/api/admin/products', data),
  update: (id, data) => axiosInstance.put(`/api/admin/products/${id}`, data),
  updateStatus: (id, data) => axiosInstance.patch(`/api/admin/products/${id}/status`, data),

  // === VARIANTS ===
  getVariants: (productId) => axiosInstance.get(`/api/admin/products/${productId}/variants`),
  createVariant: (productId, data) => axiosInstance.post(`/api/admin/products/${productId}/variants`, data),
  updateVariant: (productId, variantId, data) =>
    axiosInstance.put(`/api/admin/products/${productId}/variants/${variantId}`, data),
  deleteVariant: (productId, variantId) =>
    axiosInstance.delete(`/api/admin/products/${productId}/variants/${variantId}`),

  // === IMAGES ===
  getImages: (productId) => axiosInstance.get(`/api/admin/products/${productId}/images`),
  uploadImage: (productId, formData) =>
    axiosInstance.post(`/api/admin/products/${productId}/images`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  setPrimaryImage: (productId, imageId) =>
    axiosInstance.patch(`/api/admin/products/${productId}/images/${imageId}/primary`),
  deleteImage: (productId, imageId) =>
    axiosInstance.delete(`/api/admin/products/${productId}/images/${imageId}`),
};
