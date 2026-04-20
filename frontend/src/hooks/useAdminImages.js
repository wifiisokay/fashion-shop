import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

/** Upload ảnh chính (listing) */
export const useUploadPrimaryImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, file }) => {
      const formData = new FormData();
      formData.append('file', file);
      return productApi.uploadPrimaryImage(productId, formData);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Upload ảnh gallery theo màu */
export const useUploadColorImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, colorId, file }) => {
      const formData = new FormData();
      formData.append('file', file);
      return productApi.uploadColorImage(productId, colorId, formData);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

/** Đổi thứ tự ảnh gallery theo màu */
export const useReorderImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, imageId, newSortOrder }) => 
      productApi.reorderImage(productId, imageId, newSortOrder),
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
