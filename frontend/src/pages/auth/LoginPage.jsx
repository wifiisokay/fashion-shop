import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useLocation } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import { useLogin } from '../../hooks/useAuth';

const loginSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
  password: z.string().min(8, 'Mật khẩu phải có ít nhất 8 ký tự'),
});

const LoginPage = () => {
  const location = useLocation();
  const loginMutation = useLogin();
  
  const from = location.state?.from?.pathname || ROUTES.HOME;

  const { register, handleSubmit, formState: { errors, isSubmitting }, setError } = useForm({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data) => {
    try {
      await loginMutation.mutateAsync({ ...data, from });
    } catch (error) {
      const errorCode = error.response?.data?.errorCode;
      const fallbackMessage = 'Email hoặc mật khẩu không đúng.';

      if (errorCode === 'AUTH_004' || error.response?.status === 403) {
        setError('root', { message: 'Tài khoản của bạn đã bị khoá.' });
        return;
      }

      setError('root', { message: error.response?.data?.message || fallbackMessage });
    }
  };

  return (
    <div className="min-h-[70vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
        <div>
          <h2 className="text-center text-3xl font-bold text-gray-900">Đăng nhập</h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Hoặc{' '}
            <Link to={ROUTES.REGISTER} className="font-medium text-black hover:underline">
              đăng ký tài khoản mới
            </Link>
          </p>
        </div>
        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Email</label>
              <input
                {...register('email')}
                type="email"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="you@example.com"
              />
              {errors.email && <p className="mt-1 text-sm text-red-600">{errors.email.message}</p>}
            </div>
            <div>
              <div className="mb-1 flex items-center justify-between">
                <label className="block text-sm font-medium text-gray-700">Mật khẩu</label>
                <Link to={ROUTES.FORGOT_PASSWORD} className="text-sm font-medium text-gray-700 hover:underline">
                  Quên mật khẩu?
                </Link>
              </div>
              <input
                {...register('password')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {errors.password && <p className="mt-1 text-sm text-red-600">{errors.password.message}</p>}
            </div>
          </div>

          {errors.root && (
            <div className="p-3 bg-red-50 text-red-600 text-sm rounded-lg">
              {errors.root.message}
            </div>
          )}

          <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
            Đăng nhập
          </Button>
        </form>
      </div>
    </div>
  );
};

export default LoginPage;
