import { useCart } from '../../hooks/useCart';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice } from '../../utils/format';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import { Trash2, Plus, Minus, ShoppingCart } from 'lucide-react';

const CartPage = () => {
  const { data, isLoading, isError, updateCartItem, removeCartItem } = useCart();

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải giỏ hàng</div>;

  // Fallback mock data for preview if data is undefined
  const cartItems = data ? (data.items || []) : [];

  const total = cartItems.reduce((sum, item) => sum + item.price * item.quantity, 0);

  if (cartItems.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-24 px-4 space-y-6 bg-white rounded-3xl border border-gray-100 shadow-sm">
        <div className="w-24 h-24 bg-gray-50 rounded-full flex items-center justify-center text-gray-300 mb-2">
          <ShoppingCart className="w-12 h-12" />
        </div>
        <div className="text-center space-y-3">
          <h2 className="text-2xl font-bold text-gray-900">Giỏ hàng của bạn đang trống</h2>
          <p className="text-gray-500 max-w-md mx-auto">
            Có vẻ như bạn chưa thêm sản phẩm nào vào giỏ hàng. Hãy khám phá các sản phẩm hấp dẫn của chúng tôi nhé!
          </p>
        </div>
        <Link to={ROUTES.PRODUCTS} className="inline-block mt-4">
          <Button size="lg" className="flex items-center gap-2 px-8">
            Tiếp tục mua sắm
          </Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Giỏ hàng của bạn</h1>
      <div className="grid lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-4">
          {cartItems.map(item => (
            <div key={item.id} className="flex gap-4 bg-white p-4 rounded-2xl border border-gray-100 shadow-sm">
              <img
                src={item.imageUrl || `https://picsum.photos/seed/${item.productId}/200/200`}
                alt={item.name}
                className="w-24 h-24 object-cover rounded-xl bg-gray-100"
                referrerPolicy="no-referrer"
              />
              <div className="flex-grow flex flex-col justify-between">
                <div className="flex justify-between items-start gap-4">
                  <Link to={`/products/${item.productId}`} className="font-medium text-gray-900 line-clamp-2 hover:text-gray-600">
                    {item.name}
                  </Link>
                  <button
                    onClick={() => removeCartItem.mutate(item.id)}
                    className="text-gray-400 hover:text-red-500 transition-colors p-1"
                    title="Xóa sản phẩm"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>
                <div className="flex items-center justify-between mt-4">
                  <span className="font-bold text-gray-900">{formatPrice(item.price)}</span>
                  <div className="flex items-center gap-3 bg-gray-50 rounded-lg p-1 border border-gray-200">
                    <button
                      onClick={() => updateCartItem.mutate({ itemId: item.id, quantity: item.quantity - 1 })}
                      disabled={item.quantity <= 1}
                      className="p-1 hover:bg-white rounded-md disabled:opacity-50 transition-colors"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="w-6 text-center text-sm font-medium">{item.quantity}</span>
                    <button
                      onClick={() => updateCartItem.mutate({ itemId: item.id, quantity: item.quantity + 1 })}
                      className="p-1 hover:bg-white rounded-md transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>
        <div className="bg-white p-6 rounded-2xl h-fit border border-gray-200 shadow-sm sticky top-24">
          <h2 className="text-lg font-bold mb-4 text-gray-900">Tổng đơn hàng</h2>
          <div className="space-y-3 text-sm text-gray-600 mb-6">
            <div className="flex justify-between">
              <span>Tạm tính</span>
              <span className="font-medium text-gray-900">{formatPrice(total)}</span>
            </div>
            <div className="flex justify-between">
              <span>Phí vận chuyển</span>
              <span>Chưa tính</span>
            </div>
            <div className="border-t border-gray-200 pt-3 flex justify-between text-base font-bold text-gray-900">
              <span>Tổng cộng</span>
              <span>{formatPrice(total)}</span>
            </div>
          </div>
          <Link to={ROUTES.CHECKOUT} className="block">
            <Button className="w-full" size="lg">Thanh toán ngay</Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default CartPage;
