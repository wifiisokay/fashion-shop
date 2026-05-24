import axiosInstance from './axiosInstance';

export const cartApi = {
  get: () => axiosInstance.get('/api/cart'),
  add: (data) => axiosInstance.post('/api/cart/items', data),
  update: (itemId, quantity) => axiosInstance.patch(`/api/cart/items/${itemId}`, { quantity }),
  remove: (itemId) => axiosInstance.delete(`/api/cart/items/${itemId}`),
};
