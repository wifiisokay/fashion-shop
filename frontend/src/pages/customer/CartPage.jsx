import { Link } from 'react-router-dom';
import { Minus, Plus, ShoppingCart, Trash2 } from 'lucide-react';
import { useCart } from '../../hooks/useCart';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice } from '../../utils/format';
import { ROUTES } from '../../constants/routes';

const CartPage = () => {
  const { data, isLoading, isError, updateCartItem, removeCartItem } = useCart();

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải giỏ hàng</div>;

  const cartItems = data?.items || [];
  const total = data?.totalPrice || 0;
  const hasUnavailableItems = !!data?.hasUnavailableItems;

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

  const blockCheckout = (event) => {
    if (hasUnavailableItems) {
      event.preventDefault();
      alert('Giỏ hàng có sản phẩm không khả dụng hoặc không đủ số lượng. Vui lòng cập nhật trước khi thanh toán.');
    }
  };

  return (
    <div className="space-y-8">
      <h1 className="text-3xl font-bold text-gray-900">Giỏ hàng của bạn</h1>
      <div className="grid lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 space-y-4">
          {cartItems.map(item => (
            <div
              key={item.variantId}
              className={`flex gap-4 bg-white p-4 rounded-2xl border border-gray-100 shadow-sm ${!item.available ? 'opacity-50 grayscale' : ''}`}
            >
              <img
                src={item.primaryImageUrl || `https://picsum.photos/seed/${item.productId}/200/200`}
                alt={item.productName}
                className="w-24 h-24 object-cover rounded-xl bg-gray-100 shrink-0"
                referrerPolicy="no-referrer"
              />
              <div className="flex-grow flex flex-col justify-between">
                <div className="flex justify-between items-start gap-4">
                  <div>
                    <Link to={`/products/${item.productId}`} className="font-medium text-gray-900 line-clamp-2 hover:text-gray-600">
                      {item.productName}
                    </Link>
                    <p className="text-sm text-gray-500 mt-1">
                      {item.color} | {item.size}
                      {!item.available && (
                        <span className="ml-2 text-red-500 font-medium">
                          (Không khả dụng hoặc không đủ số lượng)
                        </span>
                      )}
                    </p>
                    {!item.available && (
                      <p className="mt-1 text-xs text-red-500">
                        Tồn kho hiện tại: {item.stockQuantity ?? 0}. Vui lòng giảm số lượng hoặc xóa sản phẩm.
                      </p>
                    )}
                  </div>
                  <button
                    onClick={() => removeCartItem.mutate(item.variantId)}
                    disabled={removeCartItem.isPending}
                    className="text-gray-400 hover:text-red-500 transition-colors p-1 shrink-0"
                    title="Xóa sản phẩm"
                  >
                    <Trash2 className="w-5 h-5" />
                  </button>
                </div>
                <div className="flex items-center justify-between mt-4">
                  <span className="font-bold text-gray-900">{formatPrice(item.unitPrice)}</span>
                  <div className="flex items-center gap-3 bg-gray-50 rounded-lg p-1 border border-gray-200">
                    <button
                      onClick={() => updateCartItem.mutate({ itemId: item.variantId, quantity: item.quantity - 1 })}
                      disabled={item.quantity <= 1 || updateCartItem.isPending}
                      className="p-1 hover:bg-white rounded-md disabled:opacity-50 transition-colors"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="w-6 text-center text-sm font-medium">{item.quantity}</span>
                    <button
                      onClick={() => updateCartItem.mutate({ itemId: item.variantId, quantity: item.quantity + 1 })}
                      disabled={!item.available || item.quantity >= item.stockQuantity || updateCartItem.isPending}
                      className="p-1 hover:bg-white rounded-md disabled:opacity-50 transition-colors"
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
          {hasUnavailableItems && (
            <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
              Giỏ hàng có sản phẩm đã ngừng bán hoặc số lượng vượt quá tồn kho. Vui lòng cập nhật trước khi thanh toán.
            </div>
          )}
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
          <Link to={ROUTES.CHECKOUT} className="block" onClick={blockCheckout}>
            <Button className="w-full" size="lg" disabled={hasUnavailableItems || cartItems.length === 0}>
              Thanh toán ngay
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
};

export default CartPage;
