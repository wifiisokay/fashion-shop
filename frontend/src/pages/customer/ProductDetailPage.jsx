import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useProduct } from '../../hooks/useProduct';
import { useCart } from '../../hooks/useCart';
import { useOutfitSuggestions } from '../../hooks/useOutfitSuggestions';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, isSaleActive } from '../../utils/format';
import { ShoppingCart, ChevronLeft, ChevronRight, Sparkles, Plus, RefreshCw } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import ReviewSection from '../../components/review/ReviewSection';
import { toast } from 'sonner';
import { cartApi } from '../../api/cartApi';
import { QUERY_KEYS } from '../../constants/queryKeys';

const COLOR_FAMILY_LABELS = {
  neutral: 'Trung tinh',
  cool: 'Tong lanh',
  warm: 'Tong am',
  earth: 'Tong dat',
  mixed: 'Phoi mau',
};

const normalizeOutfitItems = (items = []) => {
  const seenProducts = new Set();
  const list = items.filter(Boolean).filter((item) => {
    if (!item?.id) return false;
    const key = String(item.id);
    if (seenProducts.has(key)) return false;
    seenProducts.add(key);
    return true;
  });
  if (list.length === 0) return [];

  const main = list.find((item) => item.role === 'main') || list[0];
  const mainRole = main?.role || null;
  const seenRoles = new Set();
  const picks = [];

  for (const item of list) {
    if (item === main) continue;
    if (mainRole && item.role === mainRole) continue;
    if (item.role) {
      if (seenRoles.has(item.role)) continue;
      seenRoles.add(item.role);
    }
    picks.push(item);
    if (picks.length >= 2) break;
  }

  if (picks.length === 0) {
    const fallback = list.find((item) => item !== main);
    if (fallback) picks.push(fallback);
  }

  return [main, ...picks];
};

const OutfitItemCard = ({
  item,
  isMain,
  lockedSelection,
  productOverride,
  onSelectionChange,
}) => {
  const { data: productDetail, isLoading } = useProduct(item?.id);
  const productData = productOverride || productDetail;
  const colors = useMemo(
    () => (productData?.colors || []).slice().sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0) || a.id - b.id),
    [productData?.colors]
  );
  const [selectedColorId, setSelectedColorId] = useState(null);
  const [selectedSize, setSelectedSize] = useState(null);

  useEffect(() => {
    if (!productData) return;
    const preferred = lockedSelection?.colorId || item?.colorId;
    const nextColorId = colors.find((color) => color.id === preferred)?.id || colors[0]?.id || null;
    setSelectedColorId(nextColorId);
    setSelectedSize(lockedSelection?.size || null);
  }, [productData, colors, item?.colorId, lockedSelection?.colorId, lockedSelection?.size]);

  const selectedColor = useMemo(
    () => colors.find((color) => color.id === selectedColorId) || null,
    [colors, selectedColorId]
  );
  const sizes = useMemo(() => selectedColor?.sizes || [], [selectedColor]);
  const selectedVariant = sizes.find((size) => size.size === selectedSize && size.stockQuantity > 0) || null;

  useEffect(() => {
    if (!item?.id || !onSelectionChange) return;
    onSelectionChange(item.id, {
      variantId: selectedVariant?.variantId || null,
      colorId: selectedColor?.id || null,
      size: selectedSize || null,
      inStock: selectedVariant?.stockQuantity > 0,
    });
  }, [item?.id, onSelectionChange, selectedVariant, selectedColor?.id, selectedSize]);

  if (isLoading && !productOverride) {
    return (
      <div className="w-64 rounded-xl border border-gray-200 bg-white p-3">
        <div className="h-36 w-full animate-pulse rounded-lg bg-gray-100" />
        <div className="mt-3 h-3 w-3/4 animate-pulse rounded bg-gray-100" />
        <div className="mt-2 h-3 w-1/2 animate-pulse rounded bg-gray-100" />
      </div>
    );
  }

  const previewImage = selectedColor?.thumbnailUrl || item?.imageUrl || null;

  return (
    <div className="w-64 rounded-xl border border-gray-200 bg-white p-3">
      <a href={`/products/${item.id}`} className="group block">
        <div className="relative aspect-4/5 overflow-hidden rounded-lg bg-gray-100">
          {previewImage && (
            <img
              src={previewImage}
              alt={item.name}
              className="h-full w-full object-cover transition-transform group-hover:scale-105"
            />
          )}
          {isMain && (
            <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-0.5 text-xs font-medium text-gray-900 shadow">
              Đang xem
            </span>
          )}
        </div>
        <p className="mt-2 truncate text-sm font-medium text-gray-900">{item.name}</p>
        <p className="text-sm text-gray-600">{formatPrice(item.displayPrice || item.salePrice || item.price)}</p>
        {(item.colorName || item.colorFamily) && (
          <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
            {item.colorCode && (
              <span className="h-3 w-3 rounded-full border border-gray-200" style={{ backgroundColor: item.colorCode }} />
            )}
            <span className="truncate">{item.colorName || COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily}</span>
          </div>
        )}
      </a>

      <div className="mt-3 space-y-2">
        <div className="text-xs font-semibold text-gray-700">Màu sắc</div>
        <div className="flex flex-wrap gap-2">
          {(colors || []).map((color) => (
            <button
              key={color.id}
              type="button"
              disabled={!!lockedSelection}
              onClick={() => {
                if (lockedSelection) return;
                setSelectedColorId(color.id);
                setSelectedSize(null);
              }}
              className={`flex items-center gap-1.5 rounded-full border px-2 py-1 text-xs font-medium transition-colors ${
                selectedColorId === color.id
                  ? 'border-black bg-black text-white'
                  : 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
              } ${lockedSelection ? 'opacity-60 cursor-not-allowed' : ''}`}
            >
              {color.colorCode && (
                <span className="h-3 w-3 rounded-full border border-gray-200" style={{ backgroundColor: color.colorCode }} />
              )}
              {color.colorName}
            </button>
          ))}
        </div>

        <div className="text-xs font-semibold text-gray-700">Kích thước</div>
        <div className="flex flex-wrap gap-2">
          {sizes.length === 0 && <span className="text-xs text-gray-400">Chưa có size</span>}
          {sizes.map((size) => {
            const inStock = size.stockQuantity > 0;
            const isSelected = selectedSize === size.size;
            return (
              <button
                key={size.variantId}
                type="button"
                disabled={!inStock || !!lockedSelection}
                onClick={() => !lockedSelection && inStock && setSelectedSize(size.size)}
                className={`min-w-10 rounded-lg border px-2 py-1 text-xs font-medium transition-colors ${
                  isSelected
                    ? 'border-black bg-black text-white'
                    : inStock
                      ? 'border-gray-300 bg-white text-gray-700 hover:border-gray-400'
                      : 'border-gray-200 bg-gray-50 text-gray-400 cursor-not-allowed line-through'
                } ${lockedSelection ? 'opacity-60 cursor-not-allowed' : ''}`}
              >
                {size.size}
              </button>
            );
          })}
        </div>
        {lockedSelection && !lockedSelection?.size && (
          <p className="text-xs text-orange-600">Chọn size ở sản phẩm chính để thêm combo.</p>
        )}
      </div>
    </div>
  );
};

const ProductDetailPage = () => {
  const { id } = useParams();
  const queryClient = useQueryClient();
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

  const colors = useMemo(
    () => (product.colors || []).slice().sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0) || a.id - b.id),
    [product.colors]
  );
  const [selectedColorId, setSelectedColorId] = useState(() => colors[0]?.id || null);
  const [selectedSize, setSelectedSize] = useState(null);
  const [currentImageIndex, setCurrentImageIndex] = useState(0);
  const [activeInfoTab, setActiveInfoTab] = useState('similar');
  const [activeOutfitStyle, setActiveOutfitStyle] = useState('all');
  const [outfitSelections, setOutfitSelections] = useState({});
  const [addingComboKey, setAddingComboKey] = useState(null);
  const [outfitRefreshToken, setOutfitRefreshToken] = useState(0);
  const effectiveSelectedColorId = colors.some((c) => c.id === selectedColorId)
    ? selectedColorId
    : colors[0]?.id || null;
  const outfitQuery = useOutfitSuggestions(product.id, effectiveSelectedColorId, activeInfoTab === 'outfit', outfitRefreshToken);
  const outfitCombos = outfitQuery.data?.combos || [];
  const outfitTabs = useMemo(() => [
    { value: 'all', label: 'Tất cả' },
    ...outfitCombos.map((combo, index) => ({
      value: `combo-${index}`,
      label: combo.label || combo.outfitType || combo.style || `Combo ${index + 1}`,
    })),
  ], [outfitCombos]);
  const visibleOutfitCombos = outfitCombos.filter((combo) =>
    activeOutfitStyle === 'all' || `combo-${outfitCombos.indexOf(combo)}` === activeOutfitStyle
  );

  // Derive data from selectedColorId
  const selectedColor = useMemo(
    () => colors.find((c) => c.id === effectiveSelectedColorId) || null,
    [colors, effectiveSelectedColorId]
  );

  useEffect(() => {
    if (!product?.id) {
      return undefined;
    }
    sessionStorage.setItem('chatProductContext', JSON.stringify({
      productId: Number(product.id),
      colorId: effectiveSelectedColorId ?? null,
    }));
    return () => {
      const raw = sessionStorage.getItem('chatProductContext');
      if (!raw) {
        return;
      }
      try {
        const parsed = JSON.parse(raw);
        if (Number(parsed?.productId) === Number(product.id)) {
          sessionStorage.removeItem('chatProductContext');
        }
      } catch (_error) {
        sessionStorage.removeItem('chatProductContext');
      }
    };
  }, [product?.id, effectiveSelectedColorId]);

  const currentImages = useMemo(() => {
    const galleryImages = (product.images || [])
      .slice()
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0) || a.id - b.id)
      .map((img) => img.imageUrl)
      .filter(Boolean);
    const firstImage = selectedColor?.thumbnailUrl || galleryImages[0] || null;
    return Array.from(new Set([firstImage, ...galleryImages].filter(Boolean)));
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
    
  const selectedVariant = availableSizes.find((s) => s.size === selectedSize);
  const adjustment = selectedVariant?.priceAdjustment || 0;

  const originalPrice = (product.basePrice || product.price) + adjustment;
  const displayPrice = hasActiveSale ? (product.salePrice + adjustment) : originalPrice;

  const handleOutfitSelectionChange = (comboKey, itemId, selection) => {
    setOutfitSelections((prev) => ({
      ...prev,
      [comboKey]: {
        ...(prev[comboKey] || {}),
        [itemId]: selection,
      }
    }));
  };

  const handleAddCombo = async (comboKey, items) => {
    const selections = outfitSelections[comboKey] || {};
    const payloads = items.map((item) => {
      if (String(item.id) === String(product.id)) {
        return { variantId: selectedVariant?.variantId, quantity: 1 };
      }
      return { variantId: selections[item.id]?.variantId, quantity: 1 };
    });

    if (payloads.some((payload) => !payload.variantId)) {
      toast.error('Vui lòng chọn đủ màu và size cho các sản phẩm trong combo.');
      return;
    }

    try {
      setAddingComboKey(comboKey);
      await Promise.all(payloads.map((payload) => cartApi.add(payload)));
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.cart() });
      toast.success('Đã thêm combo vào giỏ hàng');
    } catch (error) {
      toast.error(error?.response?.data?.message || 'Lỗi khi thêm combo vào giỏ hàng');
    } finally {
      setAddingComboKey(null);
    }
  };

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
                <p className="text-3xl font-bold text-red-600">{formatPrice(displayPrice)}</p>
                <p className="text-xl text-gray-400 line-through">{formatPrice(originalPrice)}</p>
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
                    effectiveSelectedColorId === c.id
                      ? 'border-black bg-black text-white'
                      : 'border-gray-300 bg-white text-gray-900 hover:border-gray-400'
                  }`}>
                  {c.colorCode && (
                    <span className="w-4 h-4 rounded-full border border-gray-300"
                      style={{ backgroundColor: c.colorCode }} />
                  )}
                  {c.colorName}
                  {c.colorFamily && (
                    <span className={`rounded-full px-1.5 py-0.5 text-[10px] ${
                      effectiveSelectedColorId === c.id ? 'bg-white/15 text-white' : 'bg-gray-100 text-gray-500'
                    }`}>
                      {COLOR_FAMILY_LABELS[c.colorFamily] || c.colorFamily}
                    </span>
                  )}
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
      <div className="mb-10">
        <div className="flex flex-wrap gap-2 border-b border-gray-200">
          <button
            type="button"
            onClick={() => setActiveInfoTab('similar')}
            className={`px-4 py-3 text-sm font-medium transition-colors ${
              activeInfoTab === 'similar'
                ? 'border-b-2 border-black text-black'
                : 'text-gray-500 hover:text-gray-900'
            }`}
          >
            Sản phẩm tương tự
          </button>
          <button
            type="button"
            onClick={() => setActiveInfoTab('outfit')}
            className={`inline-flex items-center gap-2 px-4 py-3 text-sm font-medium transition-colors ${
              activeInfoTab === 'outfit'
                ? 'border-b-2 border-black text-black'
                : 'text-gray-500 hover:text-gray-900'
            }`}
          >
            <Sparkles className="h-4 w-4" />
            Gợi ý phối đồ AI
          </button>
        </div>

        {activeInfoTab === 'similar' && (
          <div className="py-6 text-sm text-gray-500">
            Các sản phẩm tương tự sẽ được hiển thị theo danh mục và phong cách của sản phẩm hiện tại.
          </div>
        )}

        {activeInfoTab === 'outfit' && (
          <div className="py-6">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-wrap gap-2">
              {outfitTabs.map(({ value, label }) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => setActiveOutfitStyle(value)}
                  className={`rounded-full border px-3 py-1.5 text-sm font-medium transition-colors ${
                    activeOutfitStyle === value
                      ? 'border-black bg-black text-white'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-gray-500'
                  }`}
                >
                  {label}
                </button>
              ))}
              </div>
              <button
                type="button"
                onClick={() => {
                  setActiveOutfitStyle('all');
                  setOutfitRefreshToken((value) => value + 1);
                }}
                disabled={outfitQuery.isFetching}
                className="inline-flex items-center gap-2 rounded-full border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors hover:border-gray-500 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RefreshCw className={`h-4 w-4 ${outfitQuery.isFetching ? 'animate-spin' : ''}`} />
                Goi y khac
              </button>
            </div>

            {outfitQuery.isLoading && (
              <div className="space-y-3">
                {[0, 1, 2].map((item) => (
                  <div key={item} className="h-40 animate-pulse rounded-xl border border-gray-200 bg-gray-100" />
                ))}
              </div>
            )}

            {outfitQuery.isError && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                Không tải được gợi ý phối đồ. Vui lòng thử lại sau.
              </div>
            )}

            {!outfitQuery.isLoading && !outfitQuery.isError && (
              <div className="space-y-4">
                {outfitQuery.data?.text && (
                  <p className="text-sm text-gray-600">{outfitQuery.data.text}</p>
                )}
                {visibleOutfitCombos.length === 0 && (
                  <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600">
                    Hiện chưa có đủ sản phẩm khác vai trò để phối thành outfit cho màu này. Hãy thử chọn màu khác hoặc quay lại sau khi shop cập nhật thêm sản phẩm.
                  </div>
                )}
                {visibleOutfitCombos.map((combo, comboIndex) => {
                  const comboKey = `${combo.label || combo.outfitType || combo.style || 'combo'}-${comboIndex}`;
                  const comboItems = normalizeOutfitItems(combo.products || combo.items || []);
                  const comboSelections = outfitSelections[comboKey] || {};
                  const isReady = comboItems.every((item) => {
                    if (String(item.id) === String(product.id)) {
                      return !!selectedVariant?.variantId;
                    }
                    return !!comboSelections[item.id]?.variantId;
                  });

                  return (
                    <div key={comboKey} className="rounded-xl border border-gray-200 bg-white p-4">
                      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
                        <div>
                          <span className="rounded-full bg-black px-2.5 py-1 text-xs font-medium text-white">
                            {combo.label || combo.outfitType || combo.style}
                          </span>
                          {(combo.description || combo.reason) && (
                            <p className="mt-2 text-sm text-gray-600">{combo.description || combo.reason}</p>
                          )}
                          {(combo.colorStory || combo.occasion) && (
                            <div className="mt-2 flex flex-wrap gap-2 text-xs text-gray-600">
                              {combo.colorStory && <span className="rounded-full bg-gray-100 px-2.5 py-1">{combo.colorStory}</span>}
                              {combo.occasion && <span className="rounded-full bg-blue-50 px-2.5 py-1 text-blue-700">{combo.occasion}</span>}
                            </div>
                          )}
                        </div>
                        <Button
                          type="button"
                          variant="outline"
                          disabled={!isReady || addingComboKey === comboKey}
                          onClick={() => handleAddCombo(comboKey, comboItems)}
                        >
                          <ShoppingCart className="mr-2 h-4 w-4" />
                          {addingComboKey === comboKey ? 'Đang thêm...' : 'Thêm cả combo vào giỏ'}
                        </Button>
                      </div>

                      <div className="flex gap-3 overflow-x-auto pb-1">
                        {comboItems.map((item, idx) => (
                          <div key={`${comboKey}-${item.id}-${item.colorId || idx}`} className="flex min-w-64 items-start gap-3">
                            {idx > 0 && <Plus className="mt-16 h-4 w-4 shrink-0 text-gray-400" />}
                            <OutfitItemCard
                              item={item}
                              isMain={String(item.id) === String(product.id)}
                              productOverride={String(item.id) === String(product.id) ? product : null}
                              lockedSelection={
                                String(item.id) === String(product.id)
                                  ? { colorId: effectiveSelectedColorId, size: selectedSize }
                                  : null
                              }
                              onSelectionChange={(itemId, selection) => handleOutfitSelectionChange(comboKey, itemId, selection)}
                            />
                          </div>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="max-w-3xl mx-auto space-y-6">
        <h2 className="text-2xl font-bold text-gray-900">Chi tiết sản phẩm</h2>
        <div className="product-description prose prose-slate max-w-none">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>
            {product.description || 'Chưa có mô tả cho sản phẩm này.'}
          </ReactMarkdown>
        </div>
      </div>
    </div>

    {/* === BOTTOM: Reviews === */}
    <div className="max-w-3xl mx-auto">
      <ReviewSection productId={product.id} />
    </div>
  </div>
  );
};

export default ProductDetailPage;
