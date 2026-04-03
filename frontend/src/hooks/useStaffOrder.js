import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { orderApi } from '../api/orderApi';

export const useStaffOrder = (id) => {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: ['staff_order', id],
    queryFn: async () => {
      const { data } = await orderApi.getOrderById(id);
      return data?.data ?? null;
    },
    enabled: !!id,
    staleTime: 5 * 60 * 1000,
  });

  const updateStatusMutation = useMutation({
    mutationFn: (status) => orderApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries(['staff_order', id]);
      queryClient.invalidateQueries(['staff_orders']);
    },
  });

  return {
    ...query,
    updateStatus: updateStatusMutation.mutateAsync,
    isUpdating: updateStatusMutation.isPending,
  };
};
