import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

/** Thêm variant cho sản phẩm */
export const useCreateVariant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, ...payload }) => productApi.createVariant(productId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Cập nhật variant */
export const useUpdateVariant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, variantId, ...payload }) =>
      productApi.updateVariant(productId, variantId, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Xóa variant */
export const useDeleteVariant = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, variantId }) => productApi.deleteVariant(productId, variantId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};
