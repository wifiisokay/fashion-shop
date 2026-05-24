import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

/** Thêm màu cho sản phẩm */
export const useCreateColor = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, ...payload }) => productApi.createColor(productId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Cập nhật màu */
export const useUpdateColor = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, colorId, ...payload }) =>
      productApi.updateColor(productId, colorId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Xóa màu */
export const useDeleteColor = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, colorId }) => productApi.deleteColor(productId, colorId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};
