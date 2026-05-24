import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { cartApi } from '../api/cartApi';
import { toast } from 'sonner';
import { useAuth } from '../contexts/AuthContext';

export const useCart = () => {
  const queryClient = useQueryClient();
  const { user } = useAuth();

  const cartQuery = useQuery({
    queryKey: QUERY_KEYS.cart(),
    queryFn: async () => {
      const { data } = await cartApi.get();
      return data?.data ?? null;
    },
    enabled: user?.role === 'CUSTOMER',
    staleTime: 0,
  });

  const addToCart = useMutation({
    mutationFn: cartApi.add,
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
      toast.success(response.data?.message || 'Đã thêm vào giỏ hàng');
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Lỗi khi thêm vào giỏ hàng');
    }
  });

  const updateCartItem = useMutation({
    mutationFn: ({ itemId, quantity }) => cartApi.update(itemId, quantity),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Lỗi khi cập nhật số lượng');
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() }); // Revert back optimistic UI or sync with server
    }
  });

  const removeCartItem = useMutation({
    mutationFn: cartApi.remove,
    onSuccess: (response) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
      toast.success(response.data?.message || 'Đã xóa sản phẩm khỏi giỏ hàng');
    },
    onError: (error) => {
      toast.error(error.response?.data?.message || 'Lỗi khi xóa sản phẩm');
    }
  });

  return {
    ...cartQuery,
    addToCart,
    updateCartItem,
    removeCartItem,
  };
};
