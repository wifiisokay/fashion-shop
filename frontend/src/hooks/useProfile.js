import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../contexts/AuthContext';
import { userApi } from '../api/userApi';

const PROFILE_QUERY_KEY = ['user', 'profile'];

/**
 * Lấy profile user hiện tại từ backend.
 * Dùng khi cần dữ liệu mới nhất (VD: trang Profile).
 * Khác AuthContext.user: cái này luôn fresh từ API, không phải từ localStorage.
 */
export const useProfile = () => {
  return useQuery({
    queryKey: PROFILE_QUERY_KEY,
    queryFn: async () => {
      const { data } = await userApi.getProfile();
      return data?.data ?? null;
    },
    staleTime: 60_000, // 1 phút
  });
};

/**
 * Cập nhật fullName và phone.
 * Sau khi thành công: invalidate query + cập nhật AuthContext để nav bar đồng bộ.
 */
export const useUpdateProfile = () => {
  const queryClient = useQueryClient();
  const { login } = useAuth();

  return useMutation({
    mutationFn: (data) => userApi.updateProfile(data),
    onSuccess: (response) => {
      const updated = response.data?.data;
      // Cập nhật cached query
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
      // Đồng bộ AuthContext để hiển thị tên mới trên nav
      if (updated) login(updated);
    },
  });
};

/**
 * Upload avatar lên Cloudinary qua backend.
 * Input: File object.
 * Constraints do backend enforce: jpeg/png/webp/gif, max 5MB.
 */
export const useUploadAvatar = () => {
  const queryClient = useQueryClient();
  const { login } = useAuth();

  return useMutation({
    mutationFn: (file) => userApi.uploadAvatar(file),
    onSuccess: (response) => {
      const updated = response.data?.data;
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
      if (updated) login(updated);
    },
  });
};

/**
 * Xóa avatar — backend xóa khỏi Cloudinary, set avatarUrl = null trong DB.
 */
export const useRemoveAvatar = () => {
  const queryClient = useQueryClient();
  const { login } = useAuth();

  return useMutation({
    mutationFn: () => userApi.removeAvatar(),
    onSuccess: (response) => {
      const updated = response.data?.data;
      queryClient.setQueryData(PROFILE_QUERY_KEY, updated);
      if (updated) login(updated);
    },
  });
};
