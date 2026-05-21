import axiosInstance from './axiosInstance';

export const reviewApi = {
  // Public
  getReviewsByProduct: (productId, params) => axiosInstance.get(`/api/products/${productId}/reviews`, { params }),
  getProductReviews:   (productId, params) => axiosInstance.get(`/api/products/${productId}/reviews`, { params }),
  getReviewStats:      (productId) => axiosInstance.get(`/api/products/${productId}/review-stats`),
  getReviewBreakdown:  (productId) => axiosInstance.get(`/api/products/${productId}/review-stats`),

  // Customer
  createReview: (data)   => axiosInstance.post('/api/reviews', data),
  updateReview: (id, data) => axiosInstance.put(`/api/reviews/${id}`, data),
  getMyReviews: (params) => axiosInstance.get('/api/reviews/me', { params }),

  // Admin
  getAllReviews: (params) => axiosInstance.get('/api/admin/reviews', { params }),
  getProductReviewStats: (params) => axiosInstance.get('/api/admin/reviews/product-stats', { params }),
};
