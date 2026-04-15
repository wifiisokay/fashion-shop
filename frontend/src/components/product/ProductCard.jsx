import { Link } from 'react-router-dom';
import { formatPrice, isSaleActive } from '../../utils/format';
import Button from '../ui/Button';
import { ShoppingCart } from 'lucide-react';

const ProductCard = ({ product, onAddToCart }) => {
  const hasActiveSale = product.salePrice && isSaleActive(product.saleStartDate, product.saleEndDate);
  const isOutOfStock = product.stock === 0;

  return (
    <div className="group flex flex-col bg-white rounded-2xl overflow-hidden border border-gray-100 shadow-sm hover:shadow-md transition-all duration-300 relative">
      <Link to={`/products/${product.id}`} className="relative aspect-[3/4] overflow-hidden bg-gray-100 block">
        <img
          src={product.imageUrl || `https://picsum.photos/seed/${product.id}/400/600`}
          alt={product.name}
          className={`w-full h-full object-cover transition-transform duration-500 ${isOutOfStock ? 'opacity-50 grayscale' : 'group-hover:scale-105'}`}
          referrerPolicy="no-referrer"
        />
        {isOutOfStock && (
          <div className="absolute inset-0 flex items-center justify-center z-20">
            <span className="bg-black/80 text-white px-4 py-2 rounded-lg font-medium text-sm">
              Hết hàng
            </span>
          </div>
        )}
        {product.isNew && !isOutOfStock && (
          <div className="absolute top-3 left-3 bg-black text-white text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-sm z-10">
            Mới
          </div>
        )}
        {hasActiveSale && !isOutOfStock && (
          <div className="absolute top-3 right-3 bg-red-500 text-white text-[10px] font-bold uppercase tracking-wider px-2 py-1 rounded-sm z-10">
            Sale
          </div>
        )}
      </Link>
      <div className="p-4 flex flex-col flex-grow">
        <Link to={`/products/${product.id}`} className="text-sm font-medium text-gray-900 hover:text-gray-600 line-clamp-2 mb-2">
          {product.name}
        </Link>
        <div className="mt-auto flex items-center justify-between">
          <div className="flex flex-col">
            {hasActiveSale ? (
              <>
                <span className="text-base font-bold text-red-600">{formatPrice(product.salePrice)}</span>
                <span className="text-xs text-gray-400 line-through">{formatPrice(product.price)}</span>
              </>
            ) : (
              <span className="text-base font-bold text-gray-900">{formatPrice(product.price)}</span>
            )}
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="p-2 rounded-full hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={() => onAddToCart(product)}
            title={isOutOfStock ? "Hết hàng" : "Thêm vào giỏ hàng"}
            disabled={isOutOfStock}
          >
            <ShoppingCart className="w-5 h-5" />
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ProductCard;
