import { useQuery } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { returnApi } from '../api/returnApi';

export const useReturns = (params = {}) => {
  return useQuery({
    queryKey: QUERY_KEYS.returns(params),
    queryFn: async () => {
      const { data } = await returnApi.getAll(params);
      return data?.data ?? null; // Assuming response format { success: true, data: { content: [], ... } }
    },
    staleTime: 60 * 1000, // 1 minute
  });
};
