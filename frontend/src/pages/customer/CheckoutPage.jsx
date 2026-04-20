import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { useCart } from '../../hooks/useCart';
import { useAddresses } from '../../hooks/useAddresses';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { formatPrice } from '../../utils/format';

const checkoutSchema = z.object({
  addressId: z.number({ required_error: 'Vui lòng chọn địa chỉ giao hàng' }),
  paymentMethod: z.enum(['COD', 'VNPAY']),
});

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { data: cartData, isLoading: cartLoading } = useCart();
  const { data: addresses = [], isLoading: addressLoading } = useAddresses();

  const defaultAddressId = addresses.find((a) => a.isDefault)?.id;

  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(checkoutSchema),
    defaultValues: {
      paymentMethod: 'COD',
      addressId: defaultAddressId,
    },
    values: {
      paymentMethod: 'COD',
      addressId: defaultAddressId,
    },
  });

  const selectedAddressId = watch('addressId');

  const cartItems = cartData?.items || [];
  const subtotal = cartData?.totalPrice || 0;
  const shippingFee = 30000;
  const total = subtotal + shippingFee;

  const onSubmit = async (data) => {
    try {
      // Mock API call to create order
      await new Promise(resolve => setTimeout(resolve, 1500));
      console.log('Order created:', { ...data, items: cartItems, total });

      if (data.paymentMethod === 'VNPAY') {
        // Mock redirect to VNPAY
        navigate(`${ROUTES.PAYMENT_RESULT}?status=success&orderId=ORD-${Date.now()}`);
      } else {
        navigate(`${ROUTES.PAYMENT_RESULT}?status=success&orderId=ORD-${Date.now()}`);
      }
    } catch (error) {
      console.error('Checkout failed', error);
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
            <div className="flex justify-between">
              <span>Phí vận chuyển</span>
              <span className="font-medium text-gray-900">{formatPrice(shippingFee)}</span>
            </div>
            <div className="border-t border-gray-200 pt-3 flex justify-between text-lg font-bold text-gray-900">
              <span>Tổng cộng</span>
              <span className="text-red-600">{formatPrice(total)}</span>
            </div>
          </div>

          <Button type="submit" className="w-full" size="lg" loading={isSubmitting} disabled={cartData?.hasUnavailableItems}>
            {cartData?.hasUnavailableItems ? 'Vui lòng cập nhật giỏ hàng' : 'Đặt hàng'}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default CheckoutPage;
