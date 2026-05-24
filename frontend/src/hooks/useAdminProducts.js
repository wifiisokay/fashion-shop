import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { productApi } from '../api/productApi';

/** Admin: listing sản phẩm có filter/pagination */
export const useAdminProducts = (params = {}) => {
  return useQuery({
    queryKey: QUERY_KEYS.adminProducts(params),
    queryFn: async () => {
      const { data } = await productApi.adminList(params);
      return data?.data ?? { content: [], totalPages: 0, totalElements: 0 };
    },
    staleTime: 2 * 60 * 1000,
  });
};

/** Admin: chi tiết sản phẩm (cho edit form) */
export const useAdminProduct = (id) => {
  return useQuery({
    queryKey: QUERY_KEYS.adminProduct(id),
    queryFn: async () => {
      const { data } = await productApi.adminGetById(id);
      return data?.data ?? null;
    },
    enabled: !!id,
  });
};

/** Tạo sản phẩm mới */
export const useCreateProduct = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => productApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
};

/** Cập nhật sản phẩm */
export const useUpdateProduct = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...payload }) => productApi.update(id, payload),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.adminProduct(variables.id) });
    },
  });
};

/** Đổi trạng thái ACTIVE / INACTIVE */
export const useUpdateProductStatus = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }) => productApi.updateStatus(id, { status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
    },
  });
};
