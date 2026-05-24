import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

export const useProduct = (id) => {
  return useQuery({
    queryKey: QUERY_KEYS.product(id),
    queryFn: async () => {
      const { data } = await productApi.getById(id);
      return data?.data ?? null;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
