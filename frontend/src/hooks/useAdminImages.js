import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

/** Upload ảnh sản phẩm (multipart) */
export const useUploadImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, file, variantId, isPrimary }) => {
      const formData = new FormData();
      formData.append('file', file);
      if (variantId) formData.append('variantId', variantId);
      if (isPrimary) formData.append('isPrimary', true);
      return productApi.uploadImage(productId, formData);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Đặt ảnh chính */
export const useSetPrimaryImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, imageId }) => productApi.setPrimaryImage(productId, imageId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Xóa ảnh */
export const useDeleteImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, imageId }) => productApi.deleteImage(productId, imageId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};
