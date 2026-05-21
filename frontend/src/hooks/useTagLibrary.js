import { useQuery } from '@tanstack/react-query';
import { productApi } from '@/api/productApi';

export const useTagLibrary = () => {
  return useQuery({
    queryKey: ['tagLibrary'],
    queryFn: async () => {
      const { data } = await productApi.getTagLibrary();
      return data.data; // ApiResponse.data contains { styleTags, occasionTags, fitTypes, colorFamilies, seasons }
    },
    staleTime: Infinity, // Tag library doesn't change often, cache it forever in this session
    cacheTime: Infinity,
    refetchOnWindowFocus: false,
  });
};
