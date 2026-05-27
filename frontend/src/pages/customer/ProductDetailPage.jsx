import { useEffect, useMemo, useState } from 'react';
import { useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import { useQueryClient } from '@tanstack/react-query';
import { useProduct } from '../../hooks/useProduct';
import { useCart } from '../../hooks/useCart';
import { useOutfitSuggestions } from '../../hooks/useOutfitSuggestions';
import Spinner from '../../components/ui/Spinner';
import Button from '../../components/ui/Button';
import { formatPrice, isSaleActive } from '../../utils/format';
import { ShoppingCart, ChevronLeft, ChevronRight, Sparkles, Plus, RefreshCw, AlertCircle } from 'lucide-react';
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

const SLOT_ROLE_LABELS = {
  top: 'Áo',
  bottom: 'Quần/Váy',
  outer: 'Khoác',
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

  const resolvedPrice = useMemo(() => {
    if (item.displayPrice || item.salePrice || item.price) {
      return item.displayPrice || item.salePrice || item.price;
    }
    if (productData) {
      const hasActiveSale = productData.isCurrentlyOnSale !== undefined ? productData.isCurrentlyOnSale : (productData.isSale && productData.salePrice);
      return hasActiveSale ? productData.salePrice : (productData.basePrice || productData.price);
    }
    return 0;
  }, [item.displayPrice, item.salePrice, item.price, productData]);

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

  const fullName = productData?.name || item?.name || '';

  return (
    <div className="w-64 rounded-xl border border-gray-200 bg-white p-3">
      <a href={`/products/${item.id}`} className="group block">
        <div className="relative aspect-4/5 overflow-hidden rounded-lg bg-gray-100">
          {previewImage && (
            <img
              src={previewImage}
              alt={fullName}
              className="h-full w-full object-cover transition-transform group-hover:scale-105"
            />
          )}
          {isMain && (
            <span className="absolute left-2 top-2 rounded-full bg-white px-2 py-0.5 text-xs font-medium text-gray-900 shadow">
              Đang xem
            </span>
          )}
          {item.role && SLOT_ROLE_LABELS[item.role] && (
            <span className="absolute right-2 top-2 rounded-full bg-black/75 px-2 py-0.5 text-[10px] font-semibold text-white backdrop-blur-xs flex items-center gap-1 shadow">
              <span>{SLOT_ROLE_LABELS[item.role]}</span>
              {item.role === 'outer' && item.optional && (
                <span className="rounded bg-amber-400 px-1 py-0.25 text-[8px] font-bold text-black uppercase">
                  Tùy chọn
                </span>
              )}
            </span>
          )}
        </div>
        <p 
          className="mt-2 text-sm font-medium text-gray-900 line-clamp-2 min-h-10 flex items-center" 
          title={fullName}
        >
          {fullName}
        </p>
        <p className="text-sm font-bold text-gray-800">{formatPrice(resolvedPrice)}</p>
        {(item.colorName || item.colorFamily || item.colorCode) && (
          <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
            {item.colorCode && (
              <span className="h-3 w-3 rounded-full border border-gray-200" style={{ backgroundColor: item.colorCode }} />
            )}
            <span className="truncate">{item.colorName || COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily || 'Màu sắc'}</span>
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
  const [activeOutfitStyle, setActiveOutfitStyle] = useState('Tất cả');
  const [outfitSelections, setOutfitSelections] = useState({});
  const [addingComboKey, setAddingComboKey] = useState(null);
  const [outfitRefreshToken, setOutfitRefreshToken] = useState(0);
  const effectiveSelectedColorId = colors.some((c) => c.id === selectedColorId)
    ? selectedColorId
    : colors[0]?.id || null;
  const outfitQuery = useOutfitSuggestions(product.id, effectiveSelectedColorId, activeInfoTab === 'outfit', outfitRefreshToken);
  const outfitCombos = outfitQuery.data?.combos || [];
  const outfitTabs = useMemo(() => {
    const labels = Array.from(
      new Set(
        outfitCombos.map((c) => c.label || c.outfitType || c.style || 'Bộ phối').filter(Boolean)
      )
    );
    return ['Tất cả', ...labels];
  }, [outfitCombos]);
  const visibleOutfitCombos = useMemo(() => {
    if (activeOutfitStyle === 'Tất cả') return outfitCombos;
    return outfitCombos.filter(
      (combo) => (combo.label || combo.outfitType || combo.style || 'Bộ phối') === activeOutfitStyle
    );
  }, [outfitCombos, activeOutfitStyle]);

  // Reset outfit active style and refresh token on color change
  useEffect(() => {
    setActiveOutfitStyle('Tất cả');
    setOutfitRefreshToken(0);
  }, [effectiveSelectedColorId]);

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

  const hasActiveSale = product.isCurrentlyOnSale !== undefined ? product.isCurrentlyOnSale : (product.isSale && product.salePrice);
    
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
          <div className="flex flex-col gap-2">
            <div className="flex items-baseline gap-4">
              {hasActiveSale ? (
                <>
                  <p className="text-3xl font-bold text-red-600">{formatPrice(displayPrice)}</p>
                  <p className="text-xl text-gray-400 line-through">{formatPrice(originalPrice)}</p>
                  <span className="bg-red-100 text-red-800 text-xs font-bold px-2.5 py-0.5 rounded-md shadow-sm">
                    Sale -{product.discountPercent || Math.round((1 - product.salePrice / product.basePrice) * 100)}%
                  </span>
                </>
              ) : (
                <p className="text-2xl font-bold text-gray-900">{formatPrice(displayPrice)}</p>
              )}
            </div>
            {hasActiveSale && product.saleEndAt && (
              <p className="text-xs font-bold text-red-500 bg-red-50 px-3 py-1.5 rounded-xl border border-red-100/50 inline-block self-start">
                ⏳ Khuyến mãi kết thúc: {dayjs(product.saleEndAt).format('HH:mm DD/MM/YYYY')}
              </p>
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

        {selectedVariant && selectedVariant.stockQuantity <= (product.lowStockThreshold !== undefined ? product.lowStockThreshold : 10) && (
          <div className="text-xs font-bold text-amber-700 bg-amber-50 px-3.5 py-2.5 rounded-xl border border-amber-200/50 flex items-center gap-2 animate-pulse mt-4">
            <AlertCircle className="w-4 h-4 text-amber-500 shrink-0" />
            <span>Chỉ còn {selectedVariant.stockQuantity} sản phẩm trong kho! Hãy nhanh tay đặt hàng.</span>
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
              {outfitTabs.map((tab) => (
                <button
                  key={tab}
                  type="button"
                  onClick={() => setActiveOutfitStyle(tab)}
                  className={`rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    activeOutfitStyle === tab
                      ? 'border-black bg-black text-white shadow-sm'
                      : 'border-gray-300 bg-white text-gray-700 hover:border-gray-500'
                  }`}
                >
                  {tab}
                </button>
              ))}
              </div>
              <button
                type="button"
                onClick={() => {
                  setActiveOutfitStyle('Tất cả');
                  setOutfitRefreshToken((value) => value + 1);
                }}
                disabled={outfitQuery.isFetching}
                className="inline-flex items-center gap-2 rounded-full border border-gray-300 px-3.5 py-1.5 text-sm font-medium text-gray-700 transition-all hover:border-gray-500 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <RefreshCw className={`h-4 w-4 ${outfitQuery.isFetching ? 'animate-spin' : ''}`} />
                Gợi ý khác
              </button>
            </div>

            {(outfitQuery.isLoading || outfitQuery.isFetching) && (
              <div className="space-y-4">
                {[0, 1, 2].map((item) => (
                  <div key={item} className="rounded-xl border border-gray-150 bg-white p-4 shadow-sm animate-pulse">
                    <div className="flex items-center gap-3 mb-4">
                      <div className="h-6 w-32 bg-gray-200 rounded-full" />
                      <div className="h-5 w-24 bg-gray-200 rounded-full" />
                    </div>
                    <div className="flex gap-4 overflow-x-auto pb-2 mb-4">
                      {[0, 1].map((pIdx) => (
                        <div key={pIdx} className="w-64 rounded-xl border border-gray-200 bg-white p-3 shrink-0">
                          <div className="aspect-4/5 w-full rounded-lg bg-gray-100" />
                          <div className="mt-3 h-4 w-3/4 rounded bg-gray-150" />
                          <div className="mt-2 h-3.5 w-1/2 rounded bg-gray-150" />
                        </div>
                      ))}
                    </div>
                    <div className="h-4 bg-gray-150 rounded w-2/3 mb-2" />
                    <div className="h-4 bg-gray-150 rounded w-1/2" />
                  </div>
                ))}
                <p className="text-center text-sm font-semibold text-gray-500 flex items-center justify-center gap-2 mt-4 animate-bounce">
                  <span>✨</span> Trí tuệ nhân tạo Gemini đang thiết kế & chấm điểm phối đồ cho bạn...
                </p>
              </div>
            )}

            {outfitQuery.isError && (
              <div className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
                Không tải được gợi ý phối đồ. Vui lòng thử lại sau.
              </div>
            )}

            {!outfitQuery.isLoading && !outfitQuery.isFetching && !outfitQuery.isError && (
              <div className="space-y-4">
                {outfitQuery.data?.text && (
                  <p className="text-sm text-gray-600 font-medium">{outfitQuery.data.text}</p>
                )}
                {visibleOutfitCombos.length === 0 && (
                  <div className="rounded-xl border border-gray-200 bg-white p-4 text-sm text-gray-600">
                    Hiện chưa có đủ sản phẩm khác vai trò để phối thành outfit cho màu này. Hãy thử chọn màu khác hoặc quay lại sau khi shop cập nhật thêm sản phẩm.
                  </div>
                )}
                {visibleOutfitCombos.map((combo, comboIndex) => {
                  const comboKey = `${combo.label || combo.outfitType || combo.style || 'combo'}-${comboIndex}`;
                  
                  const hasSlots = combo.topSlot || combo.bottomSlot;
                  let comboItems = [];
                  if (hasSlots) {
                    const topItem = combo.topSlot ? {
                      id: combo.topSlot.productId,
                      name: combo.topSlot.productName,
                      colorId: combo.topSlot.colorId,
                      colorName: combo.topSlot.colorName,
                      colorCode: combo.topSlot.colorCode,
                      colorFamily: combo.topSlot.colorFamily,
                      imageUrl: combo.topSlot.imageUrl,
                      role: 'top',
                      optional: combo.topSlot.isOptional || combo.topSlot.optional || false,
                    } : null;

                    const bottomItem = combo.bottomSlot ? {
                      id: combo.bottomSlot.productId,
                      name: combo.bottomSlot.productName,
                      colorId: combo.bottomSlot.colorId,
                      colorName: combo.bottomSlot.colorName,
                      colorCode: combo.bottomSlot.colorCode,
                      colorFamily: combo.bottomSlot.colorFamily,
                      imageUrl: combo.bottomSlot.imageUrl,
                      role: 'bottom',
                      optional: combo.bottomSlot.isOptional || combo.bottomSlot.optional || false,
                    } : null;

                    const outerItem = combo.outerSlot ? {
                      id: combo.outerSlot.productId,
                      name: combo.outerSlot.productName,
                      colorId: combo.outerSlot.colorId,
                      colorName: combo.outerSlot.colorName,
                      colorCode: combo.outerSlot.colorCode,
                      colorFamily: combo.outerSlot.colorFamily,
                      imageUrl: combo.outerSlot.imageUrl,
                      role: 'outer',
                      optional: combo.outerSlot.isOptional || combo.outerSlot.optional || false,
                    } : null;

                    comboItems = [topItem, bottomItem, outerItem].filter(Boolean);
                  } else {
                    comboItems = normalizeOutfitItems(combo.products || combo.items || []);
                  }

                  const comboSelections = outfitSelections[comboKey] || {};
                  const isReady = comboItems.every((item) => {
                    if (String(item.id) === String(product.id)) {
                      return !!selectedVariant?.variantId;
                    }
                    return !!comboSelections[item.id]?.variantId;
                  });

                  const occasions = combo.occasion ? combo.occasion.split(',').map(s => s.trim()).filter(Boolean) : [];

                  return (
                    <div key={comboKey} className="rounded-xl border border-gray-200 bg-white p-5 hover:border-gray-300 transition-colors shadow-xs">
                      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 pb-3">
                        <div className="flex items-center gap-2">
                          <span className="rounded-full bg-black px-3 py-1 text-xs font-bold text-white shadow-xs">
                            {combo.label || combo.outfitType || combo.style || 'Bộ phối'}
                          </span>
                          {combo.score !== undefined && combo.score !== null && combo.score > 0 && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 border border-amber-200 px-2.5 py-0.5 text-xs font-extrabold text-amber-700 shadow-2xs">
                              ✨ AI Match: {Math.round(combo.score * 100)}%
                            </span>
                          )}
                          {combo.provider && (
                            <span className="rounded bg-gray-100 px-1.5 py-0.5 text-[9px] font-bold text-gray-500 uppercase tracking-wider">
                              {combo.provider}
                            </span>
                          )}
                        </div>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={!isReady || addingComboKey === comboKey}
                          onClick={() => handleAddCombo(comboKey, comboItems)}
                          className="hover:bg-black hover:text-white transition-colors"
                        >
                          <ShoppingCart className="mr-1.5 h-3.5 w-3.5" />
                          {addingComboKey === comboKey ? 'Đang thêm...' : 'Thêm cả combo'}
                        </Button>
                      </div>

                      {/* Description & AI Reason */}
                      <div className="mb-4 space-y-2.5">
                        {combo.description && (
                          <p className="text-sm text-gray-600 leading-relaxed font-normal">{combo.description}</p>
                        )}
                        {combo.reason && (
                          <div className="p-3 bg-gray-50 border border-gray-100/50 rounded-xl flex gap-2.5 items-start">
                            <span className="text-sm shrink-0">💡</span>
                            <div>
                              <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider">Gợi ý từ Stylist AI</p>
                              <p className="text-xs text-gray-600 mt-0.5 leading-relaxed">{combo.reason}</p>
                            </div>
                          </div>
                        )}
                      </div>

                      {/* Color Story & Occasion Chips */}
                      {(combo.colorStory || occasions.length > 0) && (
                        <div className="mb-4 flex flex-wrap gap-2.5 items-center">
                          {combo.colorStory && (
                            <span className="inline-flex items-center gap-1.5 rounded-full bg-indigo-50 border border-indigo-100 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                              <span>🎨 Phối màu:</span>
                              <span className="font-normal">{combo.colorStory}</span>
                            </span>
                          )}
                          {occasions.map((occ) => (
                            <span key={occ} className="inline-flex items-center rounded-full bg-emerald-50 border border-emerald-100 px-2.5 py-1 text-xs font-semibold text-emerald-700">
                              📍 {occ}
                            </span>
                          ))}
                        </div>
                      )}

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
