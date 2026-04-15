import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useProduct } from '../../hooks/useProduct';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, isSaleActive } from '../../utils/format';
import { ShoppingCart, ChevronLeft, ChevronRight } from 'lucide-react';

const ProductDetailPage = () => {
  const { id } = useParams();
  const { data, isLoading, isError } = useProduct(id);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);

  // Fallback mock data
  const product = data || {
    id,
    name: 'Áo Thun Basic Nam Cao Cấp',
    price: 250000,
    salePrice: 199000,
    saleStartDate: '2026-03-01T00:00',
    saleEndDate: '2026-04-30T23:59',
    description: 'Áo thun nam chất liệu cotton 100% thoáng mát, thấm hút mồ hôi tốt. Form dáng chuẩn, dễ dàng phối hợp với nhiều trang phục khác nhau.',
    images: [
      `https://picsum.photos/seed/${id}/800/1000`,
      `https://picsum.photos/seed/${id}a/800/1000`,
    ],
    variations: [
      { id: 1, color: 'Đen', size: 'M', stock: 50, image: `https://picsum.photos/seed/${id}black/800/1000` },
      { id: 2, color: 'Đen', size: 'L', stock: 50, image: `https://picsum.photos/seed/${id}black/800/1000` },
      { id: 3, color: 'Trắng', size: 'M', stock: 50, image: `https://picsum.photos/seed/${id}white/800/1000` },
      { id: 4, color: 'Trắng', size: 'L', stock: 0, image: `https://picsum.photos/seed/${id}white/800/1000` },
    ]
  };

  // Ensure we have an array of images
  const images = [...(product.images || [product.imageUrl])];

  // Add variation images if they exist and aren't already in the array
  if (product.variations) {
    product.variations.forEach(v => {
      if (v.image && !images.includes(v.image)) {
        images.push(v.image);
      }
    });
  }

  const availableColors = Array.from(new Set(product.variations?.map(v => v.color).filter(Boolean)));
  const availableSizes = Array.from(new Set(product.variations?.map(v => v.size).filter(Boolean)));

  const [selectedColor, setSelectedColor] = useState('');
  const [selectedSize, setSelectedSize] = useState('');

  // Initialize selected color and size
  useEffect(() => {
    if (availableColors.length > 0 && !selectedColor) {
      setSelectedColor(availableColors[0]);
    }
    if (availableSizes.length > 0 && !selectedSize) {
      setSelectedSize(availableSizes[0]);
    }
  }, [availableColors, availableSizes, selectedColor, selectedSize]);

  const handleColorSelect = (color) => {
    setSelectedColor(color);
    // Find the first variation with this color that has an image
    const variationWithImage = product.variations?.find(v => v.color === color && v.image);
    if (variationWithImage) {
      const imageIndex = images.indexOf(variationWithImage.image);
      if (imageIndex !== -1) {
        setCurrentImageIndex(imageIndex);
      }
    }
  };

  const nextImage = () => {
    setCurrentImageIndex((prev) => (prev + 1) % images.length);
  };

  const prevImage = () => {
    setCurrentImageIndex((prev) => (prev - 1 + images.length) % images.length);
  };

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải thông tin sản phẩm</div>;

  const hasActiveSale = product.salePrice && isSaleActive(product.saleStartDate, product.saleEndDate);

  return (
    <div className="grid md:grid-cols-2 gap-12">
      <div className="space-y-4">
        {/* Main Image Carousel */}
        <div className="relative rounded-3xl overflow-hidden bg-gray-100 border border-gray-200 aspect-[4/5] group">
          <img
            src={images[currentImageIndex]}
            alt={`${product.name} - Image ${currentImageIndex + 1}`}
            className="w-full h-full object-cover transition-opacity duration-300"
            referrerPolicy="no-referrer"
          />

          {images.length > 1 && (
            <>
              <button
                onClick={prevImage}
                className="absolute left-4 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white text-gray-800 p-2 rounded-full shadow-md opacity-0 group-hover:opacity-100 transition-opacity"
                aria-label="Previous image"
              >
                <ChevronLeft className="w-6 h-6" />
              </button>
              <button
                onClick={nextImage}
                className="absolute right-4 top-1/2 -translate-y-1/2 bg-white/80 hover:bg-white text-gray-800 p-2 rounded-full shadow-md opacity-0 group-hover:opacity-100 transition-opacity"
                aria-label="Next image"
              >
                <ChevronRight className="w-6 h-6" />
              </button>
            </>
          )}
        </div>

        {/* Thumbnails */}
        {images.length > 1 && (
          <div className="flex gap-4 overflow-x-auto pb-2 snap-x scrollbar-hide">
            {images.map((img, idx) => (
              <button
                key={idx}
                onClick={() => setCurrentImageIndex(idx)}
                className={`relative shrink-0 w-20 h-24 rounded-xl overflow-hidden border-2 transition-all snap-start ${currentImageIndex === idx ? 'border-black' : 'border-transparent hover:border-gray-300'
                  }`}
              >
                <img
                  src={img}
                  alt={`Thumbnail ${idx + 1}`}
                  className="w-full h-full object-cover"
                  referrerPolicy="no-referrer"
                />
              </button>
            ))}
          </div>
        )}
      </div>

      <div className="flex flex-col justify-center space-y-8">
        <div className="space-y-4">
          <h1 className="text-3xl sm:text-4xl font-bold text-gray-900">{product.name}</h1>
          <div className="flex items-baseline gap-4">
            {hasActiveSale ? (
              <>
                <p className="text-3xl font-bold text-red-600">{formatPrice(product.salePrice)}</p>
                <p className="text-xl text-gray-400 line-through">{formatPrice(product.price)}</p>
                <span className="bg-red-100 text-red-800 text-xs font-semibold px-2.5 py-0.5 rounded">Sale</span>
              </>
            ) : (
              <p className="text-2xl font-bold text-gray-900">{formatPrice(product.price)}</p>
            )}
          </div>
        </div>

        {/* Variations */}
        {availableColors.length > 0 && (
          <div className="space-y-3">
            <h3 className="text-sm font-medium text-gray-900 uppercase tracking-wider">Màu sắc: <span className="text-gray-500 font-normal">{selectedColor}</span></h3>
            <div className="flex flex-wrap gap-3">
              {availableColors.map(color => (
                <button
                  key={color}
                  onClick={() => handleColorSelect(color)}
                  className={`px-4 py-2 rounded-full border text-sm font-medium transition-colors ${selectedColor === color
                      ? 'border-black bg-black text-white'
                      : 'border-gray-300 bg-white text-gray-900 hover:border-gray-400'
                    }`}
                >
                  {color}
                </button>
              ))}
            </div>
          </div>
        )}

        {availableSizes.length > 0 && (
          <div className="space-y-3">
            <h3 className="text-sm font-medium text-gray-900 uppercase tracking-wider">Kích thước: <span className="text-gray-500 font-normal">{selectedSize}</span></h3>
            <div className="flex flex-wrap gap-3">
              {availableSizes.map(size => {
                // Check if this size is available for the selected color
                const isAvailable = product.variations.some(v => v.color === selectedColor && v.size === size && parseInt(v.stock) > 0);
                return (
                  <button
                    key={size}
                    onClick={() => isAvailable && setSelectedSize(size)}
                    disabled={!isAvailable}
                    className={`min-w-[3rem] px-3 py-2 rounded-lg border text-sm font-medium transition-colors ${selectedSize === size
                        ? 'border-black bg-black text-white'
                        : isAvailable
                          ? 'border-gray-300 bg-white text-gray-900 hover:border-gray-400'
                          : 'border-gray-200 bg-gray-50 text-gray-400 cursor-not-allowed'
                      }`}
                  >
                    {size}
                  </button>
                );
              })}
            </div>
          </div>
        )}

        <div className="space-y-4">
          <h3 className="text-sm font-medium text-gray-900 uppercase tracking-wider">Mô tả sản phẩm</h3>
          <p className="text-gray-600 leading-relaxed">
            {product.description}
          </p>
        </div>

        <div className="pt-6 border-t border-gray-200">
          <Button size="lg" className="w-full sm:w-auto" onClick={() => console.log('Added to cart')}>
            <ShoppingCart className="w-5 h-5 mr-2" />
            Thêm vào giỏ hàng
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ProductDetailPage;
