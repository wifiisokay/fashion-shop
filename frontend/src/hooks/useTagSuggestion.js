import { useMutation } from '@tanstack/react-query';
import { productApi } from '@/api/productApi';
import { toast } from 'sonner';

export const useTagSuggestion = () => {
  return useMutation({
    mutationFn: async (payload) => {
      // payload: { name, description, categoryId?, material?, gender }
      const { data } = await productApi.suggestTags(payload);
      return data.data; // ProductTagSuggestResponse
    },
    onError: (error) => {
      const message = error.response?.data?.message || 'Không thể lấy gợi ý tag từ AI';
      toast.error(message);
    }
  });
};
