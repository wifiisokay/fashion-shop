import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

export const useProducts = (filters = {}) => {
  return useQuery({
    queryKey: QUERY_KEYS.products(filters),
    queryFn: async () => {
      const { data } = await productApi.getAll(filters);
      return data?.data ?? null;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
