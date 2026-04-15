import { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { MapPin, Plus, Loader2 } from 'lucide-react';
import Button from '../ui/Button';
import Spinner from '../ui/Spinner';
import axios from 'axios';
import { useAddresses } from '../../hooks/useAddresses';

/**
 * Schema align với backend AddressRequest:
 *  fullName, phone, province, provinceCode (number), district, districtCode (number),
 *  ward, wardCode (String!), street, isDefault
 *
 * wardCode là String theo GHN format (vd: "1A0807"), provinceCode và districtCode là số.
 */
const addressSchema = z.object({
  fullName: z.string().min(2, 'Tên người nhận phải có ít nhất 2 ký tự').max(100),
  phone: z
    .string()
    .regex(/^(0[3|5|7|8|9])+([0-9]{8})$/, 'Số điện thoại không hợp lệ (10 số, đầu 03/05/07/08/09)'),
  street: z.string().min(3, 'Địa chỉ cụ thể quá ngắn').max(255),
  province: z.string().min(1, 'Vui lòng chọn tỉnh/thành phố'),
  provinceCode: z.number({ invalid_type_error: 'Vui lòng chọn tỉnh/thành phố' }),
  district: z.string().min(1, 'Vui lòng chọn quận/huyện'),
  districtCode: z.number({ invalid_type_error: 'Vui lòng chọn quận/huyện' }),
  ward: z.string().min(1, 'Vui lòng chọn phường/xã'),
  wardCode: z.string().min(1, 'Vui lòng chọn phường/xã'),
  isDefault: z.boolean().optional().default(false),
});

const AddressManager = () => {
  const {
    data: addresses = [],
    isLoading,
    isError,
    createAddress,
    updateAddress,
    removeAddress,
    setDefaultAddress,
  } = useAddresses();

  const [isEditing, setIsEditing] = useState(false);
  const [currentAddress, setCurrentAddress] = useState(null);

  // Danh sách tỉnh/huyện/xã từ provinces.open-api.vn
  const [provinces, setProvinces] = useState([]);
  const [districts, setDistricts] = useState([]);
  const [wards, setWards] = useState([]);
  const [loadingDistricts, setLoadingDistricts] = useState(false);
  const [loadingWards, setLoadingWards] = useState(false);

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(addressSchema),
    defaultValues: { isDefault: false },
  });

  const selectedProvinceCode = watch('provinceCode');
  const selectedDistrictCode = watch('districtCode');

  // Fetch provinces một lần
  useEffect(() => {
    axios
      .get('https://provinces.open-api.vn/api/p/')
      .then((res) => setProvinces(res.data))
      .catch((err) => console.error('Không tải được danh sách tỉnh:', err));
  }, []);

  // Fetch districts khi chọn tỉnh
  useEffect(() => {
    if (selectedProvinceCode) {
      setLoadingDistricts(true);
      axios
        .get(`https://provinces.open-api.vn/api/p/${selectedProvinceCode}?depth=2`)
        .then((res) => setDistricts(res.data.districts || []))
        .catch((err) => console.error(err))
        .finally(() => setLoadingDistricts(false));
    } else {
      setDistricts([]);
    }
  }, [selectedProvinceCode]);

  // Fetch wards khi chọn huyện
  useEffect(() => {
    if (selectedDistrictCode) {
      setLoadingWards(true);
      axios
        .get(`https://provinces.open-api.vn/api/d/${selectedDistrictCode}?depth=2`)
        .then((res) => setWards(res.data.wards || []))
        .catch((err) => console.error(err))
        .finally(() => setLoadingWards(false));
    } else {
      setWards([]);
    }
  }, [selectedDistrictCode]);

  const handleOpenForm = (address = null) => {
    setCurrentAddress(address);
    if (address) {
      // Map backend fields vào form
      reset({
        fullName: address.fullName,
        phone: address.phone,
        street: address.street,
        province: address.province,
        provinceCode: address.provinceCode,
        district: address.district,
        districtCode: address.districtCode,
        ward: address.ward,
        wardCode: address.wardCode,
        isDefault: address.isDefault ?? false,
      });
    } else {
      reset({
        fullName: '',
        phone: '',
        street: '',
        province: '',
        provinceCode: undefined,
        district: '',
        districtCode: undefined,
        ward: '',
        wardCode: '',
        isDefault: false,
      });
    }
    setIsEditing(true);
  };

  const handleCloseForm = () => {
    setIsEditing(false);
    setCurrentAddress(null);
    reset();
  };

  const onSubmit = async (data) => {
    try {
      if (currentAddress) {
        await updateAddress.mutateAsync({ id: currentAddress.id, ...data });
      } else {
        await createAddress.mutateAsync(data);
      }
      handleCloseForm();
    } catch (err) {
      const msg = err.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.';
      alert(msg);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bạn có chắc chắn muốn xóa địa chỉ này?')) return;
    try {
      await removeAddress.mutateAsync(id);
    } catch (err) {
      alert(err.response?.data?.message || 'Xóa địa chỉ thất bại.');
    }
  };

  const handleSetDefault = async (id) => {
    try {
      await setDefaultAddress.mutateAsync(id);
    } catch (err) {
      alert(err.response?.data?.message || 'Không thể đặt địa chỉ mặc định.');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-bold text-gray-900 flex items-center gap-2">
          <MapPin className="w-5 h-5" /> Sổ địa chỉ
        </h3>
        {!isEditing && (
          <Button size="sm" onClick={() => handleOpenForm()} className="flex items-center gap-1">
            <Plus className="w-4 h-4" /> Thêm địa chỉ mới
          </Button>
        )}
      </div>

      {/* ===== FORM TẠO / CHỈNH SỬA ===== */}
      {isEditing ? (
        <div className="bg-gray-50 p-6 rounded-xl border border-gray-200">
          <h4 className="font-semibold mb-4">
            {currentAddress ? 'Sửa địa chỉ' : 'Thêm địa chỉ mới'}
          </h4>
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            {/* Tên & SĐT */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Tên người nhận
                </label>
                <input
                  {...register('fullName')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black"
                  placeholder="Nguyễn Văn A"
                />
                {errors.fullName && (
                  <p className="mt-1 text-sm text-red-600">{errors.fullName.message}</p>
                )}
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Số điện thoại
                </label>
                <input
                  {...register('phone')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black"
                  placeholder="0912345678"
                />
                {errors.phone && (
                  <p className="mt-1 text-sm text-red-600">{errors.phone.message}</p>
                )}
              </div>
            </div>

            {/* Tỉnh / Huyện / Xã */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              {/* Tỉnh/Thành */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Tỉnh/Thành phố
                </label>
                <select
                  value={selectedProvinceCode ?? ''}
                  onChange={(e) => {
                    const code = e.target.value ? Number(e.target.value) : undefined;
                    const name = e.target.options[e.target.selectedIndex].text;
                    setValue('provinceCode', code, { shouldValidate: true });
                    setValue('province', code ? name : '', { shouldValidate: true });
                    // Reset huyện/xã
                    setValue('districtCode', undefined);
                    setValue('district', '');
                    setValue('wardCode', '');
                    setValue('ward', '');
                    setDistricts([]);
                    setWards([]);
                  }}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black"
                >
                  <option value="">Chọn Tỉnh/Thành</option>
                  {provinces.map((p) => (
                    <option key={p.code} value={p.code}>
                      {p.name}
                    </option>
                  ))}
                </select>
                {errors.provinceCode && (
                  <p className="mt-1 text-sm text-red-600">{errors.provinceCode.message}</p>
                )}
              </div>

              {/* Quận/Huyện */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Quận/Huyện
                </label>
                <select
                  value={selectedDistrictCode ?? ''}
                  onChange={(e) => {
                    const code = e.target.value ? Number(e.target.value) : undefined;
                    const name = e.target.options[e.target.selectedIndex].text;
                    setValue('districtCode', code, { shouldValidate: true });
                    setValue('district', code ? name : '', { shouldValidate: true });
                    setValue('wardCode', '');
                    setValue('ward', '');
                    setWards([]);
                  }}
                  disabled={!selectedProvinceCode || loadingDistricts}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black disabled:bg-gray-100"
                >
                  <option value="">
                    {loadingDistricts ? 'Đang tải...' : 'Chọn Quận/Huyện'}
                  </option>
                  {districts.map((d) => (
                    <option key={d.code} value={d.code}>
                      {d.name}
                    </option>
                  ))}
                </select>
                {errors.districtCode && (
                  <p className="mt-1 text-sm text-red-600">{errors.districtCode.message}</p>
                )}
              </div>

              {/* Phường/Xã */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Phường/Xã
                </label>
                <select
                  {...register('wardCode')}
                  onChange={(e) => {
                    register('wardCode').onChange(e);
                    const name = e.target.options[e.target.selectedIndex].text;
                    setValue('ward', e.target.value ? name : '', { shouldValidate: true });
                  }}
                  disabled={!selectedDistrictCode || loadingWards}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black disabled:bg-gray-100"
                >
                  <option value="">
                    {loadingWards ? 'Đang tải...' : 'Chọn Phường/Xã'}
                  </option>
                  {wards.map((w) => (
                    <option key={w.code} value={String(w.code)}>
                      {w.name}
                    </option>
                  ))}
                </select>
                {errors.wardCode && (
                  <p className="mt-1 text-sm text-red-600">{errors.wardCode.message}</p>
                )}
              </div>
            </div>

            {/* Địa chỉ cụ thể */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Địa chỉ cụ thể (Số nhà, tên đường)
              </label>
              <input
                {...register('street')}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black"
                placeholder="123 Đường Giải Phóng"
              />
              {errors.street && (
                <p className="mt-1 text-sm text-red-600">{errors.street.message}</p>
              )}
            </div>

            {/* Mặc định */}
            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="isDefault"
                {...register('isDefault')}
                className="rounded border-gray-300 text-black focus:ring-black"
              />
              <label htmlFor="isDefault" className="text-sm text-gray-700">
                Đặt làm địa chỉ mặc định
              </label>
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <Button type="button" variant="outline" onClick={handleCloseForm} disabled={isSubmitting}>
                Hủy
              </Button>
              <Button type="submit" loading={isSubmitting}>
                Lưu địa chỉ
              </Button>
            </div>
          </form>
        </div>
      ) : (
        /* ===== DANH SÁCH ĐỊA CHỈ ===== */
        <div className="space-y-4">
          {isLoading ? (
            <div className="flex justify-center py-8">
              <Spinner />
            </div>
          ) : isError ? (
            <p className="text-red-500 text-center py-4">
              Không thể tải danh sách địa chỉ. Vui lòng thử lại.
            </p>
          ) : addresses.length === 0 ? (
            <div className="text-center py-10 bg-gray-50 rounded-xl border border-dashed border-gray-300">
              <MapPin className="w-8 h-8 text-gray-300 mx-auto mb-2" />
              <p className="text-gray-500">Bạn chưa có địa chỉ nào.</p>
              <p className="text-gray-400 text-sm mt-1">
                Thêm địa chỉ để thanh toán nhanh hơn.
              </p>
            </div>
          ) : (
            addresses.map((address) => (
              <div
                key={address.id}
                className={`p-4 rounded-xl border transition-colors ${
                  address.isDefault ? 'border-black bg-gray-50' : 'border-gray-200'
                } flex flex-col sm:flex-row justify-between gap-4`}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-bold text-gray-900">{address.fullName}</span>
                    <span className="text-gray-400">|</span>
                    <span className="text-gray-600">{address.phone}</span>
                    {address.isDefault && (
                      <span className="ml-1 px-2 py-0.5 bg-black text-white text-[10px] uppercase tracking-wider font-bold rounded-sm">
                        Mặc định
                      </span>
                    )}
                  </div>
                  <p className="text-gray-600 text-sm">{address.street}</p>
                  <p className="text-gray-500 text-sm">
                    {address.ward}, {address.district}, {address.province}
                  </p>
                </div>

                <div className="flex sm:flex-col items-center sm:items-end justify-between sm:justify-start gap-2 shrink-0">
                  <div className="flex gap-3">
                    <button
                      onClick={() => handleOpenForm(address)}
                      className="text-blue-600 hover:underline text-sm font-medium"
                    >
                      Sửa
                    </button>
                    <button
                      onClick={() => handleDelete(address.id)}
                      disabled={removeAddress.isPending}
                      className="text-red-600 hover:underline text-sm font-medium disabled:opacity-50"
                    >
                      {removeAddress.isPending ? (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      ) : (
                        'Xóa'
                      )}
                    </button>
                  </div>

                  {!address.isDefault && (
                    <button
                      onClick={() => handleSetDefault(address.id)}
                      disabled={setDefaultAddress.isPending}
                      className="text-sm border border-gray-300 rounded px-2 py-1 hover:bg-gray-100 transition-colors disabled:opacity-50"
                    >
                      {setDefaultAddress.isPending ? (
                        <Loader2 className="w-4 h-4 animate-spin" />
                      ) : (
                        'Thiết lập mặc định'
                      )}
                    </button>
                  )}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
};

export default AddressManager;
