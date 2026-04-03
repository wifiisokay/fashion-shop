import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import { useForgotPassword } from '../../hooks/useAuth';

const forgotPasswordSchema = z.object({
  email: z.string().min(1, 'Vui lòng nhập email').email('Email không hợp lệ'),
});

const ForgotPasswordPage = () => {
  const [submitted, setSubmitted] = useState(false);
  const forgotPasswordMutation = useForgotPassword();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    setError,
  } = useForm({
    resolver: zodResolver(forgotPasswordSchema),
  });

  const onSubmit = async (data) => {
    try {
      await forgotPasswordMutation.mutateAsync(data);
      setSubmitted(true);
    } catch (error) {
      setError('root', {
        message: error.response?.data?.message || 'Không thể xử lý yêu cầu lúc này. Vui lòng thử lại.',
      });
    }
  };

  return (
    <div className="min-h-[70vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8 bg-white p-8 rounded-2xl shadow-sm border border-gray-100">
        <div>
          <h2 className="text-center text-3xl font-bold text-gray-900">Quên mật khẩu</h2>
          <p className="mt-2 text-center text-sm text-gray-600">
            Nhập email để nhận hướng dẫn đặt lại mật khẩu.
          </p>
        </div>

        {submitted ? (
          <div className="space-y-4">
            <div className="p-3 bg-green-50 text-green-700 text-sm rounded-lg">
              Nếu email tồn tại trong hệ thống, bạn sẽ nhận được email hướng dẫn đặt lại mật khẩu.
            </div>
            <div className="text-center">
              <Link to={ROUTES.LOGIN} className="text-sm font-medium text-black hover:underline">
                Quay lại đăng nhập
              </Link>
            </div>
          </div>
        ) : (
          <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
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

            {errors.root && (
              <div className="p-3 bg-red-50 text-red-600 text-sm rounded-lg">
                {errors.root.message}
              </div>
            )}

            <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
              Gửi yêu cầu
            </Button>

            <div className="text-center">
              <Link to={ROUTES.LOGIN} className="text-sm font-medium text-black hover:underline">
                Quay lại đăng nhập
              </Link>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default ForgotPasswordPage;
