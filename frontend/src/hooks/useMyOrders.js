import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { orderApi } from '../api/orderApi';

export const useMyOrders = (params = {}) => {
  return useQuery({
    queryKey: QUERY_KEYS.myOrders(params),
    queryFn: async () => {
      const { data } = await orderApi.getMyOrders(params);
      return data?.data ?? null;
    },
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};
