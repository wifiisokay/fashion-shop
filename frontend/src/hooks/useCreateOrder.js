import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { orderApi } from '../api/orderApi';

export const useCreateOrder = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data) => orderApi.createOrder(data),
    onSuccess: (_response, variables) => {
      if (variables?.paymentMethod !== 'VNPAY') {
        queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
      }
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
    },
  });
};
