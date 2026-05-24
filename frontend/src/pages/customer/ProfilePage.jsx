import { useState, useRef } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '../../contexts/AuthContext';
import { useProfile, useUpdateProfile, useUploadAvatar, useRemoveAvatar } from '../../hooks/useProfile';
import Button from '../../components/ui/Button';
import { User, Camera, Briefcase, ShieldCheck, Trash2, AlertCircle, CheckCircle2 } from 'lucide-react';
import AddressManager from '../../components/customer/AddressManager';
import dayjs from 'dayjs';

// ─── Zod schema ────────────────────────────────────────────────────────────────
// Backend UpdateProfileRequest: { fullName (required, max 100), phone (optional, VN pattern) }
const profileSchema = z.object({
  fullName: z
    .string()
    .min(2, 'Họ tên phải có ít nhất 2 ký tự')
    .max(100, 'Họ tên tối đa 100 ký tự'),
  phone: z
    .string()
    .regex(/^(0[3|5|7|8|9])+([0-9]{8})$/, 'Số điện thoại không hợp lệ (VD: 0912345678)')
    .optional()
    .or(z.literal('')),
});

// ─── Toast inline component ────────────────────────────────────────────────────
const Toast = ({ type, message }) => {
  if (!message) return null;
  const isSuccess = type === 'success';
  return (
    <div className={`flex items-center gap-2 p-3 text-sm rounded-lg border ${
      isSuccess
        ? 'bg-green-50 text-green-700 border-green-200'
        : 'bg-red-50 text-red-700 border-red-200'
    }`}>
      {isSuccess
        ? <CheckCircle2 className="w-4 h-4 shrink-0" />
        : <AlertCircle className="w-4 h-4 shrink-0" />
      }
      {message}
    </div>
  );
};

// ─── Main Component ─────────────────────────────────────────────────────────────
const ProfilePage = () => {
  const { user } = useAuth();

  // Fresh data từ API (không phải localStorage cached)
  const { data: profile, isLoading: profileLoading } = useProfile();

  const updateProfile = useUpdateProfile();
  const uploadAvatar  = useUploadAvatar();
  const removeAvatar  = useRemoveAvatar();

  const [toast, setToast] = useState({ type: '', message: '' });
  const fileInputRef = useRef(null);

  // Dùng profile từ API nếu có, fallback về AuthContext.user
  const displayUser = profile ?? user;

  const isCustomer     = displayUser?.role === 'CUSTOMER';
  const isStaffOrAdmin = displayUser?.role === 'EMPLOYEE' || displayUser?.role === 'ADMIN';

  const showToast = (type, message) => {
    setToast({ type, message });
    setTimeout(() => setToast({ type: '', message: '' }), 4000);
  };

  // ─── Form ─────────────────────────────────────────────────────────────────────
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(profileSchema),
    values: {
      // `values` (không phải defaultValues) để re-populate khi query data load xong
      fullName: displayUser?.fullName ?? '',
      phone:    displayUser?.phone    ?? '',
    },
  });

  const onSubmit = async (formData) => {
    try {
      await updateProfile.mutateAsync({
        fullName: formData.fullName,
        phone:    formData.phone || undefined,
      });
      showToast('success', 'Cập nhật thông tin thành công!');
    } catch (err) {
      const msg = err?.response?.data?.message ?? 'Có lỗi xảy ra, vui lòng thử lại.';
      showToast('error', msg);
    }
  };

  // ─── Avatar handlers ──────────────────────────────────────────────────────────
  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      await uploadAvatar.mutateAsync(file);
      showToast('success', 'Cập nhật ảnh đại diện thành công!');
    } catch (err) {
      const msg = err?.response?.data?.message ?? 'Upload thất bại. Chỉ hỗ trợ jpeg/png/webp/gif, tối đa 5MB.';
      showToast('error', msg);
    }
    // Reset input để có thể chọn lại cùng file
    e.target.value = '';
  };

  const handleRemoveAvatar = async () => {
    if (!displayUser?.avatarUrl) return;
    try {
      await removeAvatar.mutateAsync();
      showToast('success', 'Đã xóa ảnh đại diện.');
    } catch (err) {
      const msg = err?.response?.data?.message ?? 'Xóa ảnh thất bại, vui lòng thử lại.';
      showToast('error', msg);
    }
  };

  const isAvatarLoading = uploadAvatar.isPending || removeAvatar.isPending;

  // ─── Render ───────────────────────────────────────────────────────────────────
  if (profileLoading) {
    return (
      <div className="flex items-center justify-center min-h-[300px]">
        <div className="w-8 h-8 border-4 border-gray-200 border-t-black rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Hồ sơ cá nhân</h1>

      {/* Toast thông báo */}
      {toast.message && <Toast type={toast.type} message={toast.message} />}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">

        {/* ── Cột trái: Avatar & Basic Info ── */}
        <div className="lg:col-span-1 space-y-6">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 text-center">

            {/* Avatar */}
            <div className="relative inline-block mb-4">
              <div className="w-32 h-32 mx-auto bg-gray-100 rounded-full flex items-center justify-center text-gray-400 overflow-hidden border-4 border-white shadow-md">
                {isAvatarLoading ? (
                  <div className="w-8 h-8 border-4 border-gray-300 border-t-black rounded-full animate-spin" />
                ) : displayUser?.avatarUrl ? (
                  <img
                    src={displayUser.avatarUrl}
                    alt="Avatar"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <User className="w-16 h-16" />
                )}
              </div>

              {/* Nút upload */}
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={isAvatarLoading}
                className="absolute bottom-1 right-1 bg-black text-white p-2 rounded-full hover:bg-gray-800 transition-colors shadow-sm disabled:opacity-50"
                title="Đổi ảnh đại diện"
              >
                <Camera className="w-4 h-4" />
              </button>

              <input
                type="file"
                ref={fileInputRef}
                onChange={handleAvatarChange}
                accept="image/jpeg,image/png,image/webp,image/gif"
                className="hidden"
              />
            </div>

            <h2 className="text-xl font-bold text-gray-900">{displayUser?.fullName}</h2>
            <p className="text-gray-500 text-sm mb-1">{displayUser?.email}</p>

            {/* Ngày tạo tài khoản */}
            {displayUser?.createdAt && (
              <p className="text-gray-400 text-xs mb-3">
                Thành viên từ {dayjs(displayUser.createdAt).format('MM/YYYY')}
              </p>
            )}

            {/* Badge role */}
            <span className={`inline-flex items-center gap-1.5 px-3 py-1 text-xs font-bold rounded-full uppercase tracking-wider ${
              displayUser?.role === 'ADMIN'    ? 'bg-red-100 text-red-700'  :
              displayUser?.role === 'EMPLOYEE' ? 'bg-blue-100 text-blue-700' :
              'bg-gray-100 text-gray-700'
            }`}>
              {displayUser?.role === 'ADMIN'    && <ShieldCheck className="w-3.5 h-3.5" />}
              {displayUser?.role === 'EMPLOYEE' && <Briefcase className="w-3.5 h-3.5" />}
              {displayUser?.role}
            </span>

            {/* Nút xóa avatar (chỉ hiện khi có avatar) */}
            {displayUser?.avatarUrl && (
              <button
                type="button"
                onClick={handleRemoveAvatar}
                disabled={isAvatarLoading}
                className="mt-3 flex items-center gap-1 text-xs text-red-500 hover:text-red-700 mx-auto transition-colors disabled:opacity-50"
              >
                <Trash2 className="w-3.5 h-3.5" />
                Xóa ảnh đại diện
              </button>
            )}
          </div>

          {/* Thông tin công tác (Staff/Admin) */}
          {isStaffOrAdmin && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 space-y-4">
              <h3 className="font-semibold text-gray-900 flex items-center gap-2">
                <Briefcase className="w-5 h-5 text-gray-500" />
                Thông tin công tác
              </h3>
              <div className="space-y-3 text-sm">
                <div className="flex justify-between py-2 border-b border-gray-100">
                  <span className="text-gray-500">Mã nhân viên</span>
                  <span className="font-medium text-gray-900">EMP-{displayUser?.userId ?? '---'}</span>
                </div>
                <div className="flex justify-between py-2 border-b border-gray-100">
                  <span className="text-gray-500">Phòng ban</span>
                  <span className="font-medium text-gray-900">
                    {displayUser?.role === 'ADMIN' ? 'Ban Giám Đốc' : 'Vận hành & CSKH'}
                  </span>
                </div>
                <div className="flex justify-between py-2">
                  <span className="text-gray-500">Trạng thái</span>
                  <span className={`font-medium ${displayUser?.status === 'ACTIVE' ? 'text-green-600' : 'text-red-500'}`}>
                    {displayUser?.status === 'ACTIVE' ? 'Đang làm việc' : 'Đã khóa'}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* ── Cột phải: Form cập nhật & Address ── */}
        <div className="lg:col-span-2 space-y-8">
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="p-6 border-b border-gray-200 bg-gray-50">
              <h2 className="text-lg font-semibold text-gray-900">Thông tin cơ bản</h2>
              <p className="text-sm text-gray-500 mt-1">Cập nhật họ tên và số điện thoại</p>
            </div>

            <form onSubmit={handleSubmit(onSubmit)} className="p-6 space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

                {/* Họ và tên */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Họ và tên <span className="text-red-500">*</span>
                  </label>
                  <input
                    {...register('fullName')}
                    type="text"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent disabled:bg-gray-50"
                  />
                  {errors.fullName && (
                    <p className="mt-1 text-xs text-red-600">{errors.fullName.message}</p>
                  )}
                </div>

                {/* Số điện thoại */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Số điện thoại
                  </label>
                  <input
                    {...register('phone')}
                    type="tel"
                    placeholder="0912345678"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent disabled:bg-gray-50"
                  />
                  {errors.phone && (
                    <p className="mt-1 text-xs text-red-600">{errors.phone.message}</p>
                  )}
                </div>

                {/* Email (readonly — không thể đổi) */}
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">
                    Email
                  </label>
                  <input
                    type="email"
                    value={displayUser?.email ?? ''}
                    disabled
                    className="w-full px-3 py-2 border border-gray-200 rounded-lg bg-gray-50 text-gray-400 cursor-not-allowed"
                  />
                  <p className="mt-1 text-xs text-gray-400">Email không thể thay đổi</p>
                </div>

              </div>

              <div className="pt-4 flex justify-end border-t border-gray-100">
                <Button
                  type="submit"
                  loading={isSubmitting || updateProfile.isPending}
                >
                  Lưu thay đổi
                </Button>
              </div>
            </form>
          </div>

          {/* Sổ địa chỉ (Customer only) */}
          {isCustomer && (
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden p-6">
              <AddressManager />
            </div>
          )}
        </div>

      </div>
    </div>
  );
};

export default ProfilePage;
