import { useQuery } from '@tanstack/react-query';
import { orderApi } from '../api/orderApi';
import { QUERY_KEYS } from '../constants/queryKeys';

export const useStaffOrders = (filters = {}) => {
  return useQuery({
    queryKey: QUERY_KEYS.staffOrders(filters),
    queryFn: async () => {
      const { data } = await orderApi.getAllOrders(filters);
      return data?.data ?? null;
    },
    staleTime: 5 * 60 * 1000,
  });
};
