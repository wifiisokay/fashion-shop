import axios from 'axios';
import { toast } from 'sonner';

const axiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  withCredentials: true,
});

axiosInstance.interceptors.response.use(
  (res) => res,
  (error) => {
    const status = error.response?.status;
    const requestPath = error.config?.url || '';

    const isPublicAuthRequest = ['/auth/login', '/auth/register', '/auth/forgot-password', '/auth/reset-password']
      .some((path) => requestPath.includes(path));

    if (status === 401 && !isPublicAuthRequest) {
      const message = 'Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại';
      toast.error(message, { id: 'auth-session-expired' });
      window.dispatchEvent(new CustomEvent('auth:unauthorized', {
        detail: { message },
      }));
      const protectedPrefixes = ['/cart', '/checkout', '/orders', '/profile', '/returns', '/admin', '/staff'];
      if (protectedPrefixes.some((prefix) => window.location.pathname.startsWith(prefix))) {
        window.location.assign('/login');
      }
    }

    return Promise.reject(error);
  }
);

export default axiosInstance;
