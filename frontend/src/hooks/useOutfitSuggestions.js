import { useQuery } from '@tanstack/react-query';
import { chatApi } from '../api/chatApi';
import { QUERY_KEYS } from '../constants/queryKeys';

export const useOutfitSuggestions = (productId, colorId, enabled = false, refreshToken = 0) => {
  return useQuery({
    queryKey: QUERY_KEYS.outfitSuggestions(productId, colorId, refreshToken),
    queryFn: () => chatApi.getOutfitSuggestions(productId, colorId, refreshToken > 0),
    enabled: enabled && !!productId,
    staleTime: refreshToken > 0 ? 0 : 24 * 60 * 60 * 1000,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: 1,
  });
};
