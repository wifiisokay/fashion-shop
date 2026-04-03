import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { useAuth as useAuthContext } from '../contexts/AuthContext';
import { ROUTES } from '../constants/routes';

export const useLogin = () => {
  const navigate = useNavigate();
  const { login } = useAuthContext();

  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (response, variables) => {
      login(response.data?.data);
      const from = variables?.from || ROUTES.HOME;
      navigate(from, { replace: true });
    },
  });
};

export const useRegister = () => {
  const navigate = useNavigate();
  const { login } = useAuthContext();

  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (response) => {
      login(response.data?.data);
      navigate(ROUTES.HOME, { replace: true });
    },
  });
};

export const useLogout = () => {
  const navigate = useNavigate();
  const { logout } = useAuthContext();

  return useMutation({
    mutationFn: authApi.logout,
    onSuccess: async () => {
      await logout({ skipRequest: true });
      navigate(ROUTES.LOGIN, { replace: true });
    },
    onSettled: async () => {
      await logout({ skipRequest: true });
    },
  });
};

export const useChangePassword = () => {
  return useMutation({
    mutationFn: authApi.changePassword,
  });
};

export const useForgotPassword = () => {
  return useMutation({
    mutationFn: authApi.forgotPassword,
  });
};

export const useResetPassword = () => {
  const navigate = useNavigate();

  return useMutation({
    mutationFn: authApi.resetPassword,
    onSuccess: () => {
      navigate(ROUTES.LOGIN, {
        replace: true,
        state: { message: 'Đặt lại mật khẩu thành công. Vui lòng đăng nhập.' },
      });
    },
  });
};
