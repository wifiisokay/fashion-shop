import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { QUERY_KEYS } from '../constants/queryKeys';
import { categoryApi } from '../api/categoryApi';

/** Lấy cây danh mục (public) */
export const useCategories = () => {
  return useQuery({
    queryKey: QUERY_KEYS.categories(),
    queryFn: async () => {
      const { data } = await categoryApi.getTree();
      return data?.data ?? [];
    },
    staleTime: 10 * 60 * 1000, // 10 phút — categories ít thay đổi
  });
};

/** Tạo danh mục mới */
export const useCreateCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => categoryApi.create(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories() });
    },
  });
};

/** Cập nhật danh mục */
export const useUpdateCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...payload }) => categoryApi.update(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories() });
    },
  });
};

/** Xóa danh mục */
export const useDeleteCategory = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id) => categoryApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.categories() });
    },
  });
};
