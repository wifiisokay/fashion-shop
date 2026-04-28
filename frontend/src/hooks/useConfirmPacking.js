import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { orderApi } from '../api/orderApi';

export const useConfirmPacking = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ orderId, data }) => orderApi.confirmPacking(orderId, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.staffOrder(variables.orderId) });
      queryClient.invalidateQueries({ queryKey: ['staff', 'orders'] });
    },
  });
};
