import { useState, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useProduct } from '../../hooks/useProduct';
import { useCart } from '../../hooks/useCart';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, isSaleActive } from '../../utils/format';
import { ShoppingCart, ChevronLeft, ChevronRight } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const ProductDetailPage = () => {
  const { id } = useParams();
  const { data, isLoading } = useProduct(id);
  const { addToCart } = useCart();

  // Fallback mock data — sẽ xóa khi backend sẵn sàng
  const product = data || {
    id,
    name: 'Áo Thun Basic Nam Cao Cấp',
    basePrice: 250000,
    salePrice: 199000,
    isSale: true,
    saleStartDate: '2026-03-01T00:00',
    saleEndDate: '2026-04-30T23:59',
    fitType: 'Regular',
    season: '4 mùa',
    description: '## Đặc điểm nổi bật\n\n- **Chất liệu cotton 100%** thoáng mát, thấm hút mồ hôi tốt\n- Form dáng *slim fit* chuẩn, tôn dáng\n- Dễ dàng phối hợp với nhiều trang phục khác nhau\n\n### Hướng dẫn bảo quản\n\n1. Giặt máy ở nhiệt độ dưới 30°C\n2. Không sử dụng chất tẩy mạnh\n3. Phơi khô tự nhiên, tránh ánh nắng trực tiếp\n\n> *Áo thun basic — món đồ không thể thiếu trong tủ đồ mỗi người*',
    images: [
      { id: 99, imageUrl: `https://picsum.photos/seed/${id}/800/1000`, isPrimary: true, colorId: null },
    ],
    colors: [
      {
        id: 10, colorName: 'Đen', colorCode: '#1A1A1A',
        images: [
          { id: 1, imageUrl: `https://picsum.photos/seed/${id}b1/800/1000`, sortOrder: 1 },
          { id: 2, imageUrl: `https://picsum.photos/seed/${id}b2/800/1000`, sortOrder: 2 },
        ],
        sizes: [
          { variantId: 100, size: 'M', stockQuantity: 50, priceAdjustment: 0 },
          { variantId: 101, size: 'L', stockQuantity: 30, priceAdjustment: 0 },
          { variantId: 102, size: 'XL', stockQuantity: 0, priceAdjustment: 10000 },
        ],
      },
      {
        id: 11, colorName: 'Trắng', colorCode: '#F5F5F5',
        images: [
          { id: 3, imageUrl: `https://picsum.photos/seed/${id}w1/800/1000`, sortOrder: 1 },
        ],
        sizes: [
          { variantId: 200, size: 'M', stockQuantity: 20, priceAdjustment: 0 },
          { variantId: 201, size: 'L', stockQuantity: 0, priceAdjustment: 0 },
        ],
      },
    ],
  };

  const colors = useMemo(() => product.colors || [], [product.colors]);
  const [selectedColorId, setSelectedColorId] = useState(() => colors[0]?.id || null);
  const [selectedSize, setSelectedSize] = useState(null);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);

  // Derive data from selectedColorId
  const selectedColor = useMemo(
    () => colors.find((c) => c.id === selectedColorId) || null,
    [colors, selectedColorId]
  );

  const currentImages = useMemo(() => {
    if (!selectedColor?.images?.length) {
      // Fallback: ảnh chung (colorId=null)
      return (product.images || [])
        .sort((a, b) => {
          if (a.isPrimary) return -1;
          if (b.isPrimary) return 1;
          return (a.sortOrder || 0) - (b.sortOrder || 0);
        })
        .map((img) => img.imageUrl);
    }
    return selectedColor.images
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map((img) => img.imageUrl);
  }, [selectedColor, product.images]);

  const availableSizes = useMemo(
    () => selectedColor?.sizes || [],
    [selectedColor]
  );

  // Handle color change — reset gallery and size
  const handleColorSelect = (colorId) => {
    setSelectedColorId(colorId);
    setCurrentImageIndex(0);
    setSelectedSize(null);
  };

  const nextImage = () => setCurrentImageIndex((prev) => (prev + 1) % currentImages.length);
  const prevImage = () => setCurrentImageIndex((prev) => (prev - 1 + currentImages.length) % currentImages.length);

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;

  const hasActiveSale = product.isSale && product.salePrice &&
    (product.saleStartDate ? isSaleActive(product.saleStartDate, product.saleEndDate) : true);
  const displayPrice = hasActiveSale ? product.salePrice : (product.basePrice || product.price);

  return (
    <div className="space-y-12">
      <div className="grid md:grid-cols-2 gap-12">
      {/* === LEFT: Gallery === */}
      <div className="space-y-4">
        <div className="relative rounded-3xl overflow-hidden bg-gray-100 border border-gray-200 aspect-4/5 group">
          {currentImages[currentImageIndex] ? (
            <img
              src={currentImages[currentImageIndex]}
              alt={`${product.name} - ${selectedColor?.colorName || ''}`}
              className="w-full h-full object-cover transition-opacity duration-300"
              loading="eager"
              referrerPolicy="no-referrer"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center text-gray-400">
              Không có ảnh
            </div>
          )}

          {currentImages.length > 1 && (
            <>
              <button onClick={prevImage}
                className="absolute left-4 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white text-gray-800 p-2 rounded-full shadow-md opacity-0 group-hover:opacity-100 transition-opacity"
                aria-label="Previous image">
                <ChevronLeft className="w-6 h-6" />
              </button>
              <button onClick={nextImage}
                className="absolute right-4 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white text-gray-800 p-2 rounded-full shadow-md opacity-0 group-hover:opacity-100 transition-opacity"
                aria-label="Next image">
                <ChevronRight className="w-6 h-6" />
              </button>
            </>
          )}
        </div>

        {/* Thumbnails */}
        {currentImages.length > 1 && (
          <div className="flex gap-3 overflow-x-auto pb-2 snap-x scrollbar-hide">
            {currentImages.map((img, idx) => (
              <button key={idx} onClick={() => setCurrentImageIndex(idx)}
                className={`relative shrink-0 w-20 h-24 rounded-xl overflow-hidden border-2 transition-all snap-start ${
                  currentImageIndex === idx ? 'border-black' : 'border-transparent hover:border-gray-300'
                }`}>
                <img src={img} alt={`Thumbnail ${idx + 1}`}
                  className="w-full h-full object-cover"
                  loading="lazy" referrerPolicy="no-referrer" />
              </button>
            ))}
          </div>
        )}
      </div>

      {/* === RIGHT: Info === */}
      <div className="flex flex-col justify-center space-y-8">
        <div className="space-y-4">
          <h1 className="text-3xl sm:text-4xl font-bold text-gray-900">{product.name}</h1>

          {/* Meta badges */}
          {(product.fitType || product.season) && (
            <div className="flex gap-2">
              {product.fitType && (
                <span className="text-xs font-medium px-2.5 py-0.5 rounded bg-gray-100 text-gray-600">{product.fitType}</span>
              )}
              {product.season && (
                <span className="text-xs font-medium px-2.5 py-0.5 rounded bg-blue-50 text-blue-600">{product.season}</span>
              )}
            </div>
          )}

          {/* Price */}
          <div className="flex items-baseline gap-4">
            {hasActiveSale ? (
              <>
                <p className="text-3xl font-bold text-red-600">{formatPrice(product.salePrice)}</p>
                <p className="text-xl text-gray-400 line-through">{formatPrice(product.basePrice || product.price)}</p>
                <span className="bg-red-100 text-red-800 text-xs font-semibold px-2.5 py-0.5 rounded">Sale</span>
              </>
            ) : (
              <p className="text-2xl font-bold text-gray-900">{formatPrice(displayPrice)}</p>
            )}
          </div>
        </div>

        {/* Color swatches */}
        {colors.length > 0 && (
          <div className="space-y-3">
            <h3 className="text-sm font-medium text-gray-900 uppercase tracking-wider">
              Màu sắc: <span className="text-gray-500 font-normal">{selectedColor?.colorName}</span>
            </h3>
            <div className="flex flex-wrap gap-3">
              {colors.map((c) => (
                <button key={c.id} onClick={() => handleColorSelect(c.id)}
                  className={`flex items-center gap-2 px-4 py-2 rounded-full border text-sm font-medium transition-colors ${
                    selectedColorId === c.id
                      ? 'border-black bg-black text-white'
                      : 'border-gray-300 bg-white text-gray-900 hover:border-gray-400'
                  }`}>
                  {c.colorCode && (
                    <span className="w-4 h-4 rounded-full border border-gray-300"
                      style={{ backgroundColor: c.colorCode }} />
                  )}
                  {c.colorName}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Size buttons */}
        {availableSizes.length > 0 && (
          <div className="space-y-3">
            <h3 className="text-sm font-medium text-gray-900 uppercase tracking-wider">
              Kích thước: <span className="text-gray-500 font-normal">{selectedSize || '—'}</span>
            </h3>
            <div className="flex flex-wrap gap-3">
              {availableSizes.map((s) => {
                const inStock = s.stockQuantity > 0;
                const isSelected = selectedSize === s.size;
                return (
                  <button key={s.variantId} onClick={() => inStock && setSelectedSize(s.size)}
                    disabled={!inStock}
                    className={`min-w-12 px-3 py-2 rounded-lg border text-sm font-medium transition-colors ${
                      isSelected
                        ? 'border-black bg-black text-white'
                        : inStock
                          ? 'border-gray-300 bg-white text-gray-900 hover:border-gray-400'
                          : 'border-gray-200 bg-gray-50 text-gray-400 cursor-not-allowed line-through'
                    }`}>
                    {s.size}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {/* Add to cart */}
        <div className="pt-6 border-t border-gray-200">
          <Button size="lg" className="w-full sm:w-auto"
            disabled={!selectedSize || addToCart.isPending}
            loading={addToCart.isPending}
            onClick={() => {
              const variant = availableSizes.find((s) => s.size === selectedSize);
              if (variant) {
                addToCart.mutate({ variantId: variant.variantId, quantity: 1 });
              }
            }}>
            <ShoppingCart className="w-5 h-5 mr-2" />
            {selectedSize ? 'Thêm vào giỏ hàng' : 'Chọn kích thước'}
          </Button>
        </div>
      </div>
    </div>

    {/* === BOTTOM: Description === */}
    <div className="mt-16 pt-10 border-t border-gray-200">
      <div className="max-w-3xl mx-auto space-y-6">
        <h2 className="text-2xl font-bold text-gray-900">Chi tiết sản phẩm</h2>
        <div className="product-description prose prose-slate max-w-none">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {product.description || 'Chưa có mô tả cho sản phẩm này.'}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  </div>
  );
};

export default ProductDetailPage;
