import { useQuery } from '@tanstack/react-query';
import { productApi } from '../api/productApi';
import { QUERY_KEYS } from '../constants/queryKeys';

export const useAiInsight = (productId) => {
  return useQuery({
    queryKey: QUERY_KEYS.aiInsight(productId),
    queryFn: () => productApi.getAiInsight(productId).then((res) => res.data.data),
    enabled: !!productId,
    staleTime: 12 * 60 * 60 * 1000, // 12 hours cache
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: 1,
  });
};
