import { useQuery } from '@tanstack/react-query';
import { staffInventoryApi } from '../api/staffInventoryApi';

export const useStaffInventory = (params = {}) => {
  return useQuery({
    queryKey: ['staff', 'inventory', params],
    queryFn: async () => {
      const { data } = await staffInventoryApi.getInventory(params);
      return data?.data ?? { items: [], totalElements: 0, totalPages: 0, page: 0, size: 10 };
    },
    staleTime: 1 * 60 * 1000, // 1 minute
  });
};
