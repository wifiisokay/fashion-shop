import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { cartApi } from '../api/cartApi';

export const useCart = () => {
  const queryClient = useQueryClient();

  const cartQuery = useQuery({
    queryKey: QUERY_KEYS.cart(),
    queryFn: async () => {
      const { data } = await cartApi.get();
      return data?.data ?? null;
    },
    staleTime: 0,
  });

  const addToCart = useMutation({
    mutationFn: cartApi.add,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
    },
  });

  const updateCartItem = useMutation({
    mutationFn: ({ itemId, quantity }) => cartApi.update(itemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
    },
  });

  const removeCartItem = useMutation({
    mutationFn: cartApi.remove,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
    },
  });

  return {
    ...cartQuery,
    addToCart,
    updateCartItem,
    removeCartItem,
  };
};
