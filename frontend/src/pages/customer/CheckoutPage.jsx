import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { useCart } from '../../hooks/useCart';
import { useAddresses } from '../../hooks/useAddresses';
import { useShippingFee } from '../../hooks/useShippingFee';
import { useCreateOrder } from '../../hooks/useCreateOrder';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { formatPrice } from '../../utils/format';
import { useState } from 'react';

const checkoutSchema = z.object({
  addressId: z.coerce.number({ required_error: 'Vui lòng chọn địa chỉ giao hàng' }).min(1, 'Vui lòng chọn địa chỉ giao hàng'),
  paymentMethod: z.enum(['COD', 'VNPAY']),
  note: z.string().optional(),
});

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { data: cartData, isLoading: cartLoading } = useCart();
  const { data: addresses = [], isLoading: addressLoading } = useAddresses();
  const createOrder = useCreateOrder();
  const [error, setError] = useState(null);

  const defaultAddressId = addresses.find((a) => a.isDefault)?.id;

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(checkoutSchema),
    defaultValues: {
      paymentMethod: 'COD',
      addressId: defaultAddressId,
      note: '',
    },
    values: {
      paymentMethod: 'COD',
      addressId: defaultAddressId,
      note: '',
    },
  });

  const selectedAddressId = watch('addressId');

  const cartItems = cartData?.items || [];
  const subtotal = cartData?.totalPrice || 0;

  // Tính tổng cân nặng ước tính từ giỏ hàng (gram)
  const totalWeight = cartItems.reduce((sum, item) => 
    sum + (item.estimatedWeight || 300) * item.quantity, 0) || null;
  
  // GHN Shipping API
  const { 
    data: shippingData, 
    isLoading: shippingLoading, 
    isError: shippingError 
  } = useShippingFee(selectedAddressId, subtotal, totalWeight);

  const shippingFee = shippingData?.fee || 0;
  const total = subtotal + shippingFee;

  const onSubmit = async (formData) => {
    setError(null);
    try {
      const res = await createOrder.mutateAsync({
        addressId: formData.addressId,
        paymentMethod: formData.paymentMethod,
        shippingFee: shippingFee,
        note: formData.note || null,
        estimatedDays: shippingData?.estimatedDays || null,
      });

      const result = res?.data?.data;

      if (formData.paymentMethod === 'VNPAY' && result?.paymentUrl) {
        window.location.href = result.paymentUrl;
      } else {
        navigate(`${ROUTES.PAYMENT_RESULT}?status=success&orderId=${result?.orderId}`);
      }
    } catch (err) {
      const msg = err?.response?.data?.message || 'Đặt hàng thất bại. Vui lòng thử lại.';
      setError(msg);
    }
  };

  if (cartLoading || addressLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;

  if (cartItems.length === 0) {
    return (
      <div className="text-center py-32 space-y-6">
        <h2 className="text-2xl font-bold">Giỏ hàng trống</h2>
        <Link to={ROUTES.PRODUCTS}><Button>Tiếp tục mua sắm</Button></Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Thanh toán</h1>

      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-xl text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="grid lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-6">
          <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-6">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-bold text-gray-900">Địa chỉ giao hàng</h2>
              <Link to={ROUTES.PROFILE} className="text-sm text-blue-600 hover:underline font-medium">
                Quản lý địa chỉ
              </Link>
            </div>

            {errors.addressId && <p className="text-sm text-red-600">{errors.addressId.message}</p>}

            {addresses.length === 0 ? (
              <div className="text-center py-6 bg-gray-50 rounded-xl border border-dashed border-gray-300">
                <p className="text-gray-500 text-sm">Bạn chưa có địa chỉ nào.</p>
                <Link to={ROUTES.PROFILE} className="text-sm text-blue-600 hover:underline font-medium mt-1 inline-block">
                  Thêm địa chỉ ngay
                </Link>
              </div>
            ) : (
              <div className="space-y-3">
                {addresses.map((address) => (
                  <label
                    key={address.id}
                    className={`flex items-start p-4 border rounded-xl cursor-pointer transition-colors ${
                      Number(selectedAddressId) === address.id
                        ? 'border-black bg-gray-50'
                        : 'border-gray-200 hover:bg-gray-50'
                    }`}
                  >
                    <input
                      type="radio"
                      value={address.id}
                      {...register('addressId', { valueAsNumber: true })}
                      className="mt-1 w-4 h-4 text-black focus:ring-black border-gray-300"
                    />
                    <div className="ml-3 space-y-1 w-full">
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
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-6">
            <h2 className="text-xl font-bold text-gray-900">Phương thức thanh toán</h2>
            <div className="space-y-3">
              <label className="flex items-center p-4 border border-gray-200 rounded-xl cursor-pointer hover:bg-gray-50 transition-colors">
                <input
                  type="radio"
                  value="COD"
                  {...register('paymentMethod')}
                  className="w-4 h-4 text-black focus:ring-black border-gray-300"
                />
                <span className="ml-3 font-medium text-gray-900">Thanh toán khi nhận hàng (COD)</span>
              </label>
              <label className="flex items-center p-4 border border-gray-200 rounded-xl cursor-pointer hover:bg-gray-50 transition-colors">
                <input
                  type="radio"
                  value="VNPAY"
                  {...register('paymentMethod')}
                  className="w-4 h-4 text-black focus:ring-black border-gray-300"
                />
                <span className="ml-3 font-medium text-gray-900">Thanh toán qua VNPAY</span>
              </label>
            </div>
          </div>

          {/* Ghi chú */}
          <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm space-y-4">
            <h2 className="text-xl font-bold text-gray-900">Ghi chú</h2>
            <textarea
              {...register('note')}
              placeholder="Ghi chú cho đơn hàng (không bắt buộc)..."
              rows={3}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-gray-200 shadow-sm h-fit sticky top-24">
          <h2 className="text-lg font-bold mb-4 text-gray-900">Đơn hàng của bạn</h2>

          <div className="space-y-4 mb-6 max-h-60 overflow-y-auto pr-2">
            {cartItems.map(item => (
              <div key={item.variantId} className={`flex justify-between text-sm ${!item.available ? 'opacity-50 line-through' : ''}`}>
                <div className="flex flex-col flex-1 pr-4">
                  <span className="text-gray-600 line-clamp-1">
                    {item.quantity} x {item.productName}
                  </span>
                  <span className="text-xs text-gray-400">
                    {item.color} | {item.size}
                    {!item.available && <span className="ml-1 text-red-500">(Hết hàng)</span>}
                  </span>
                </div>
                <span className="font-medium text-gray-900 whitespace-nowrap mt-0.5">
                  {formatPrice(item.subtotal)}
                </span>
              </div>
            ))}
          </div>

          <div className="space-y-3 text-sm text-gray-600 mb-6 border-t border-gray-200 pt-4">
            <div className="flex justify-between">
              <span>Tạm tính</span>
              <span className="font-medium text-gray-900">{formatPrice(subtotal)}</span>
            </div>
            <div className="flex justify-between items-start">
              <div className="flex flex-col">
                <span>Phí vận chuyển</span>
                {shippingData?.estimatedDateText && (
                  <span className="text-xs text-green-600 mt-0.5">{shippingData.estimatedDateText}</span>
                )}
                {shippingData?.fallback && (
                  <span className="text-xs text-amber-600 mt-0.5">Phí ước tính (GHN đang bảo trì)</span>
                )}
              </div>
              <div className="font-medium text-gray-900 text-right mt-0.5">
                {shippingLoading ? (
                  <div className="w-16 h-5 bg-gray-200 animate-pulse rounded"></div>
                ) : !selectedAddressId ? (
                  <span className="text-gray-400 text-sm font-normal">Chưa chọn địa chỉ</span>
                ) : shippingError ? (
                  <span className="text-red-500 text-sm font-normal">Lỗi tính phí</span>
                ) : (
                  formatPrice(shippingFee)
                )}
              </div>
            </div>
            <div className="border-t border-gray-200 pt-3 flex justify-between text-lg font-bold text-gray-900">
              <span>Tổng cộng</span>
              <span className="text-red-600">{formatPrice(total)}</span>
            </div>
          </div>

          <Button 
            type="submit" 
            className="w-full" 
            size="lg" 
            loading={isSubmitting || createOrder.isPending} 
            disabled={cartData?.hasUnavailableItems || shippingLoading || (!shippingData && !!selectedAddressId)}
          >
            {cartData?.hasUnavailableItems ? 'Vui lòng cập nhật giỏ hàng' : 'Đặt hàng'}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default CheckoutPage;
