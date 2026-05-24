import { useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

export const useUploadColorThumbnail = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, colorId, file }) => {
      const formData = new FormData();
      formData.append('file', file);
      return productApi.uploadColorThumbnail(productId, colorId, formData);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

export const useUploadGalleryImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, file }) => {
      const formData = new FormData();
      formData.append('file', file);
      return productApi.uploadGalleryImage(productId, formData);
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};

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

export const useDeleteImage = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ productId, imageId }) => productApi.deleteImage(productId, imageId),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.productId) });
    },
  });
};
