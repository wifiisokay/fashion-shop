import { useQuery } from '@tanstack/react-query';
import { shippingApi } from '../api/shippingApi';
import { QUERY_KEYS } from '../constants/queryKeys';

export const useShippingFee = (
  addressId,
  orderValue = 0,
  totalWeight = null,
  items = [],
  enabled = true
) => {
  return useQuery({
    queryKey: QUERY_KEYS.shippingFee({ addressId, orderValue, totalWeight, items }),
    queryFn: async () => {
      const { data } = await shippingApi.calculateFee({
        addressId,
        orderValue,
        totalWeight,
        items,
      });
      return data?.data ?? null;
    },
    enabled: enabled && !!addressId && items.length > 0,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  });
};
