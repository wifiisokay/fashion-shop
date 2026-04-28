import { useQuery } from '@tanstack/react-query';
import { shippingApi } from '../api/shippingApi';
import { QUERY_KEYS } from '../constants/queryKeys';

/**
 * Hook tính phí vận chuyển GHN
 * @param {number|null} addressId ID của địa chỉ giao hàng, null nếu chưa chọn
 * @param {number} orderValue Tổng giá trị đơn hàng (dùng tính bảo hiểm, mặc định 0)
 * @param {number|null} totalWeight Tổng cân nặng ước tính (gram), null → dùng default
 */
export const useShippingFee = (addressId, orderValue = 0, totalWeight = null) => {
  return useQuery({
    queryKey: QUERY_KEYS.shippingFee({ addressId, orderValue, totalWeight }),
    queryFn: async () => {
      const { data } = await shippingApi.calculateFee({ addressId, orderValue, totalWeight });
      return data?.data ?? null; // Trả về ShippingFeeResponse
    },
    // Chỉ chạy query nếu user đã chọn địa chỉ hợp lệ
    enabled: !!addressId,
    // Cùng district:ward fee sẽ được backend cache 5 phút
    // Ta cũng cache ở frontend 5 phút để tránh spam request khi user đổi qua lại địa chỉ
    staleTime: 5 * 60 * 1000, 
    // Retry 1 lần nếu gặp lỗi mạng
    retry: 1,
  });
};
