import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { addressApi } from '../api/addressApi';

const QUERY_KEY = ['addresses'];

/**
 * Hook quản lý địa chỉ — kết nối real API backend.
 *
 * Mapping field names:
 *  Backend: fullName, province, provinceCode, district, districtCode, ward, wardCode, street, isDefault
 *  (wardCode là String, ví dụ "1A0807")
 */
export const useAddresses = () => {
  const queryClient = useQueryClient();

  const query = useQuery({
    queryKey: QUERY_KEY,
    queryFn: async () => {
      const { data } = await addressApi.getAll();
      return data?.data ?? [];
    },
    staleTime: 30_000,
  });

  /** Tạo địa chỉ mới */
  const createAddress = useMutation({
    mutationFn: (formData) => addressApi.create(formData),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  });

  /** Cập nhật địa chỉ */
  const updateAddress = useMutation({
    mutationFn: ({ id, ...formData }) => addressApi.update(id, formData),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  });

  /** Xóa địa chỉ — backend tự set default mới khi cần */
  const removeAddress = useMutation({
    mutationFn: (id) => addressApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  });

  /** Đặt địa chỉ mặc định — idempotent */
  const setDefaultAddress = useMutation({
    mutationFn: (id) => addressApi.setDefault(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  });

  return {
    ...query,
    createAddress,
    updateAddress,
    removeAddress,
    setDefaultAddress,
  };
};
