import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate, Link } from 'react-router-dom';
import { useCart } from '../../hooks/useCart';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { formatPrice } from '../../utils/format';

const checkoutSchema = z.object({
  fullName: z.string().min(2, 'Vui lòng nhập họ tên'),
  phone: z.string().regex(/(84|0[3|5|7|8|9])+([0-9]{8})\b/, 'Số điện thoại không hợp lệ'),
  address: z.string().min(10, 'Vui lòng nhập địa chỉ chi tiết'),
  paymentMethod: z.enum(['COD', 'VNPAY']),
});

const CheckoutPage = () => {
  const navigate = useNavigate();
  const { data: cartData, isLoading } = useCart();
  
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(checkoutSchema),
    defaultValues: {
      paymentMethod: 'COD'
    }
  });

  const cartItems = cartData?.items || [
    { id: 1, name: 'Áo Thun Basic Nam', price: 250000, quantity: 2 },
    { id: 2, name: 'Váy Hoa Mùa Hè', price: 450000, quantity: 1 },
  ];
  const subtotal = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const shippingFee = 30000; // Mock shipping fee
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

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;

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
            <h2 className="text-xl font-bold text-gray-900">Thông tin giao hàng</h2>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Họ và tên</label>
                <input
                  {...register('fullName')}
                  type="text"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                  placeholder="Nguyễn Văn A"
                />
                {errors.fullName && <p className="mt-1 text-sm text-red-600">{errors.fullName.message}</p>}
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Số điện thoại</label>
                <input
                  {...register('phone')}
                  type="tel"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                  placeholder="0912345678"
                />
                {errors.phone && <p className="mt-1 text-sm text-red-600">{errors.phone.message}</p>}
              </div>
              
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Địa chỉ chi tiết</label>
                <textarea
                  {...register('address')}
                  rows={3}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-black focus:border-transparent"
                  placeholder="Số nhà, tên đường, phường/xã, quận/huyện, tỉnh/thành phố"
                />
                {errors.address && <p className="mt-1 text-sm text-red-600">{errors.address.message}</p>}
              </div>
            </div>
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
              <div key={item.id} className="flex justify-between text-sm">
                <span className="text-gray-600 line-clamp-1 pr-4">
                  {item.quantity} x {item.name}
                </span>
                <span className="font-medium text-gray-900 whitespace-nowrap">
                  {formatPrice(item.price * item.quantity)}
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
          
          <Button type="submit" className="w-full" size="lg" loading={isSubmitting}>
            Đặt hàng
          </Button>
        </div>
      </form>
    </div>
  );
};

export default CheckoutPage;
