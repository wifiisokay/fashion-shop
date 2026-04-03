import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../../contexts/AuthContext';
import Button from '../../components/ui/Button';
import { User } from 'lucide-react';
import { useChangePassword } from '../../hooks/useAuth';

const profileSchema = z.object({
  name: z.string().min(2, 'Tên phải có ít nhất 2 ký tự'),
  phone: z.string().regex(/(84|0[3|5|7|8|9])+([0-9]{8})\b/, 'Số điện thoại không hợp lệ').optional().or(z.literal('')),
  address: z.string().optional(),
});

const changePasswordSchema = z.object({
  oldPassword: z.string().min(1, 'Vui lòng nhập mật khẩu hiện tại'),
  newPassword: z.string().min(8, 'Mật khẩu mới phải có ít nhất 8 ký tự'),
  confirmPassword: z.string().min(1, 'Vui lòng xác nhận mật khẩu mới'),
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: 'Mật khẩu xác nhận không khớp',
  path: ['confirmPassword'],
});

const ProfilePage = () => {
  const { user } = useAuth();
  const changePasswordMutation = useChangePassword();
  
  const { register, handleSubmit, formState: { errors, isSubmitting, isSubmitSuccessful } } = useForm({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      name: user?.fullName || '',
      phone: '0912345678', // Mock
      address: '123 Đường ABC, TP.HCM', // Mock
    }
  });

  const {
    register: registerPassword,
    handleSubmit: handleSubmitPassword,
    formState: { errors: passwordErrors, isSubmitting: isChangingPassword, isSubmitSuccessful: isPasswordSubmitSuccessful },
    reset: resetPasswordForm,
    setError: setPasswordError,
  } = useForm({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: {
      oldPassword: '',
      newPassword: '',
      confirmPassword: '',
    },
  });

  const onSubmit = async (data) => {
    try {
      // Mock API update
      await new Promise(resolve => setTimeout(resolve, 1000));
      console.log('Profile updated', data);
    } catch (error) {
      console.error(error);
    }
  };

  const onSubmitChangePassword = async (data) => {
    try {
      await changePasswordMutation.mutateAsync({
        oldPassword: data.oldPassword,
        newPassword: data.newPassword,
      });
      resetPasswordForm();
    } catch (error) {
      const errorCode = error.response?.data?.errorCode;

      if (errorCode === 'AUTH_003' || error.response?.status === 401) {
        setPasswordError('oldPassword', { message: 'Mật khẩu hiện tại không đúng' });
        return;
      }

      if (errorCode === 'AUTH_007') {
        setPasswordError('newPassword', { message: 'Mật khẩu mới không được trùng mật khẩu cũ' });
        return;
      }

      setPasswordError('root', { message: error.response?.data?.message || 'Không thể đổi mật khẩu. Vui lòng thử lại.' });
    }
  };

  return (
    <div className="max-w-2xl mx-auto space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Hồ sơ cá nhân</h1>
      
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-6 sm:p-8 border-b border-gray-200 flex items-center gap-6 bg-gray-50">
          <div className="w-20 h-20 bg-gray-200 rounded-full flex items-center justify-center text-gray-500">
            <User className="w-10 h-10" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-gray-900">{user?.fullName}</h2>
            <p className="text-gray-500">{user?.email || 'user@example.com'}</p>
            <span className="inline-block mt-2 px-2.5 py-0.5 bg-black text-white text-xs font-bold rounded-full uppercase tracking-wider">
              {user?.role}
            </span>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="p-6 sm:p-8 space-y-6">
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Họ và tên</label>
              <input
                {...register('name')}
                type="text"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
              />
              {errors.name && <p className="mt-1 text-sm text-red-600">{errors.name.message}</p>}
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại</label>
              <input
                {...register('phone')}
                type="tel"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
              />
              {errors.phone && <p className="mt-1 text-sm text-red-600">{errors.phone.message}</p>}
            </div>
            
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Địa chỉ mặc định</label>
              <textarea
                {...register('address')}
                rows={3}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
              />
              {errors.address && <p className="mt-1 text-sm text-red-600">{errors.address.message}</p>}
            </div>
          </div>

          {isSubmitSuccessful && (
            <div className="p-3 bg-green-50 text-green-700 text-sm rounded-lg">
              Cập nhật thông tin thành công!
            </div>
          )}

          <div className="pt-4 flex justify-end">
            <Button type="submit" loading={isSubmitting}>Lưu thay đổi</Button>
          </div>
        </form>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="p-6 sm:p-8 border-b border-gray-200 bg-gray-50">
          <h2 className="text-xl font-bold text-gray-900">Bảo mật</h2>
          <p className="text-sm text-gray-600 mt-1">Đổi mật khẩu để bảo vệ tài khoản của bạn.</p>
        </div>

        <form onSubmit={handleSubmitPassword(onSubmitChangePassword)} className="p-6 sm:p-8 space-y-6">
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu hiện tại</label>
              <input
                {...registerPassword('oldPassword')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {passwordErrors.oldPassword && <p className="mt-1 text-sm text-red-600">{passwordErrors.oldPassword.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Mật khẩu mới</label>
              <input
                {...registerPassword('newPassword')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {passwordErrors.newPassword && <p className="mt-1 text-sm text-red-600">{passwordErrors.newPassword.message}</p>}
            </div>

            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">Xác nhận mật khẩu mới</label>
              <input
                {...registerPassword('confirmPassword')}
                type="password"
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                placeholder="••••••••"
              />
              {passwordErrors.confirmPassword && <p className="mt-1 text-sm text-red-600">{passwordErrors.confirmPassword.message}</p>}
            </div>
          </div>

          {passwordErrors.root && (
            <div className="p-3 bg-red-50 text-red-700 text-sm rounded-lg">
              {passwordErrors.root.message}
            </div>
          )}

          {isPasswordSubmitSuccessful && (
            <div className="p-3 bg-green-50 text-green-700 text-sm rounded-lg">
              Đổi mật khẩu thành công!
            </div>
          )}

          <div className="pt-2 flex justify-end">
            <Button type="submit" loading={isChangingPassword}>Cập nhật mật khẩu</Button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default ProfilePage;
