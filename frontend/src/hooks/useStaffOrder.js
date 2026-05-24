import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { orderApi } from '../api/orderApi';

export const useStaffOrder = (id) => {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: QUERY_KEYS.staffOrder(id),
    queryFn: async () => {
      const { data } = await orderApi.getOrderById(id);
      return data?.data ?? null;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });

  const updateStatusMutation = useMutation({
    mutationFn: (data) => orderApi.updateStatus(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.staffOrder(id) });
      queryClient.invalidateQueries({ queryKey: ['staff', 'orders'] });
    },
  });

  return {
    ...query,
    updateStatus: updateStatusMutation.mutateAsync,
    isUpdating: updateStatusMutation.isPending,
  };
};
