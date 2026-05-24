import axiosInstance from './axiosInstance';

export const shippingApi = {
  /**
   * Tính phí giao hàng qua GHN
   * @param {Object} data - payload
   * @param {number} data.addressId - ID địa chỉ của user
   * @param {number} data.orderValue - Tổng giá trị đơn hàng (VNĐ)
   * @returns {Promise} response chứa thông tin phí ship
   */
  calculateFee: (data) => axiosInstance.post('/api/shipping/fee', data),
};
