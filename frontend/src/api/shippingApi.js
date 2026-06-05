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
  /**
   * Xem trước phí vận chuyển thực tế qua GHN (Staff/Admin)
   * @param {number|string} orderId - ID đơn hàng
   * @param {Object} data - Payload kích thước/cân nặng
   * @param {number} data.actualWeight - Cân nặng thực (gram)
   * @param {number} data.packageLength - Chiều dài (cm)
   * @param {number} data.packageWidth - Chiều rộng (cm)
   * @param {number} data.packageHeight - Chiều cao (cm)
   * @returns {Promise} response chứa customerShippingFee, actualGhnFee, difference
   */
  previewActualFee: (orderId, data) => axiosInstance.post(`/api/staff/orders/${orderId}/shipping/preview`, data),
};
