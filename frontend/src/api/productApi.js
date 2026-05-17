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
  uploadColorThumbnail: (productId, colorId, formData) =>
    axiosInstance.post(`/api/admin/products/${productId}/images/colors/${colorId}/thumbnail`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  uploadGalleryImage: (productId, formData) =>
    axiosInstance.post(`/api/admin/products/${productId}/images/gallery`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }),
  reorderImage: (productId, imageId, newSortOrder) =>
    axiosInstance.patch(`/api/admin/products/${productId}/images/${imageId}/reorder`, { sortOrder: newSortOrder }),
  deleteImage: (productId, imageId) =>
    axiosInstance.delete(`/api/admin/products/${productId}/images/${imageId}`),

  // === COLORS ===
  getColors: (productId) => axiosInstance.get(`/api/admin/products/${productId}/colors`),
  createColor: (productId, data) => axiosInstance.post(`/api/admin/products/${productId}/colors`, data),
  updateColor: (productId, colorId, data) =>
    axiosInstance.put(`/api/admin/products/${productId}/colors/${colorId}`, data),
  deleteColor: (productId, colorId) =>
    axiosInstance.delete(`/api/admin/products/${productId}/colors/${colorId}`),
};
