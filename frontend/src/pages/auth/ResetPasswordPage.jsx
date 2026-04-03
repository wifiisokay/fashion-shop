import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import { useResetPassword } from '../../hooks/useAuth';

const resetPasswordSchema = z.object({
  newPassword: z.string().min(8, 'Mật khẩu mới phải có ít nhất 8 ký tự'),
  confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu'),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: 'Mật khẩu xác nhận không khớp',
  path: ['confirmPassword'],
});

const ResetPasswordPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const resetPasswordMutation = useResetPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm({
    resolver: zodResolver(resetPasswordSchema),
  });

  const onSubmit = async (data) => {
    try {
      await resetPasswordMutation.mutateAsync({
        token,
        newPassword: data.newPassword,
      });
    } catch (error) {
      const errorCode = error.response?.data?.errorCode;

      if (errorCode === 'AUTH_006') {
        setError('root', {
          message: 'Link đặt lại mật khẩu đã hết hạn hoặc đã được sử dụng.',
        });
        return;
      }

      if (errorCode === 'AUTH_007') {
        setError('newPassword', {
          message: 'Mật khẩu mới không được trùng mật khẩu cũ.',
        });
        return;
      }

      setError('root', {
        message: error.response?.data?.message || 'Không thể đặt lại mật khẩu. Vui lòng thử lại.',
      });
    }
  };

  if (!token) {
    return (
      <div className="min-h-[70vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full space-y-6 bg-white p-8 rounded-2xl shadow-sm border border-gray-100 text-center">
          <h2 className="text-2xl font-bold text-gray-900">Link không hợp lệ</h2>
          <p className="text-sm text-gray-600">Liên kết đặt lại mật khẩu không tồn tại hoặc đã bị thay đổi.</p>
          <Link to={ROUTES.FORGOT_PASSWORD} className="text-sm font-medium text-black hover:underline">
            Gửi lại email đặt lại mật khẩu
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-[70vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
        <div>
          <h2 className="text-center text-3xl font-bold text-gray-900">Đặt lại mật khẩu</h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Nhập mật khẩu mới cho tài khoản của bạn.
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu mới</label>
              <input
                {...register('newPassword')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {errors.newPassword && <p className="mt-1 text-sm text-red-600">{errors.newPassword.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Xác nhận mật khẩu mới</label>
              <input
                {...register('confirmPassword')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {errors.confirmPassword && <p className="mt-1 text-sm text-red-600">{errors.confirmPassword.message}</p>}
            </div>
          </div>

          {errors.root && (
            <div className="p-3 bg-red-50 text-red-600 text-sm rounded-lg space-y-2">
              <p>{errors.root.message}</p>
              <Link to={ROUTES.FORGOT_PASSWORD} className="font-medium underline">
                Gửi lại email
              </Link>
            </div>
          )}

          <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
            Xác nhận đặt lại mật khẩu
          </Button>
        </form>
      </div>
    </div>
  );
};

export default ResetPasswordPage;
