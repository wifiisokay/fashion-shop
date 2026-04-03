import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { orderApi } from '../api/orderApi';

export const useMyOrder = (id) => {
  return useQuery({
    queryKey: QUERY_KEYS.myOrder(id),
    queryFn: async () => {
      const { data } = await orderApi.getMyOrderById(id);
      return data?.data ?? null;
    },
    enabled: !!id,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });
};
