import { useState, useRef, useEffect, useMemo } from 'react';
import { X, Send, Bot, User, ExternalLink, History, AlertCircle, ShoppingCart, Sparkles } from 'lucide-react';
import { useChatMessages } from '../../hooks/useChatMessages';
import { useCart } from '../../hooks/useCart';
import { useProduct } from '../../hooks/useProduct';
import { clsx } from 'clsx';
import ReactMarkdown from 'react-markdown';
import { formatPrice } from '../../utils/format';

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
  dress: 'Váy/Đầm',
};

const uniqueProducts = (items = []) => {
  const seen = new Set();
  return items.filter((item) => {
    if (!item?.id) return false;
    const key = `${item.id}-${item.colorId || 'default'}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const colorLabel = (item) => {
  if (!item?.colorName && !item?.colorFamily) return null;
  return [item.colorName, COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily].filter(Boolean).join(' / ');
};

const buildChatKey = (...parts) =>
  parts
    .filter((part) => part !== undefined && part !== null && part !== '')
    .map(String)
    .join('-');

const getProductCardKey = (product, index, messageId, section) =>
  buildChatKey(
    section || 'product',
    messageId || 'msg',
    product?.id || product?.productId || 'unknown-product',
    product?.colorId || product?.selectedColorId || product?.color?.id || 'no-color',
    index
  );

const getComboKey = (combo, index, messageId) => {
  let itemId = 'no-item';
  let colorId = 'no-color';
  if (combo.topSlot) {
    itemId = combo.topSlot.productId || itemId;
    colorId = combo.topSlot.colorId || colorId;
  } else {
    const firstItem = (combo.products || combo.items || [])[0];
    itemId = firstItem?.id || firstItem?.productId || itemId;
    colorId = firstItem?.colorId || colorId;
  }
  return buildChatKey(
    'combo',
    messageId || 'msg',
    index,
    itemId,
    colorId
  );
};

const ChatOutfitItemCardSkeleton = ({ role, optional }) => {
  return (
    <div className="w-24 shrink-0 block relative">
      <div className="relative aspect-4/5 overflow-hidden rounded-lg bg-gray-100 border border-gray-100 animate-pulse">
        {role && SLOT_ROLE_LABELS[role] && (
          <span className="absolute left-1 top-1 rounded bg-black/75 px-1 py-0.25 text-[8px] font-bold text-white uppercase backdrop-blur-xs">
            {SLOT_ROLE_LABELS[role]}
          </span>
        )}
        {role === 'outer' && optional && (
          <span className="absolute right-1 top-1 rounded bg-amber-400 px-1 py-0.25 text-[7px] font-extrabold text-black uppercase">
            T.Chọn
          </span>
        )}
      </div>
      <div className="mt-1 h-3 w-full bg-gray-100 rounded animate-pulse" />
      <div className="mt-1 h-3 w-2/3 bg-gray-100 rounded animate-pulse" />
      <div className="mt-1 h-2.5 w-1/2 bg-gray-100 rounded animate-pulse" />
    </div>
  );
};

const ChatOutfitItemCard = ({ item, combo, comboIdx, idx }) => {
  const { data: productDetail, isLoading } = useProduct(item?.id);
  const productData = productDetail;

  const colors = useMemo(
    () => (productData?.colors || []).slice().sort((a, b) => (a.displayOrder || 0) - (b.displayOrder || 0) || a.id - b.id),
    [productData?.colors]
  );

  const selectedColor = useMemo(() => {
    if (!productData) return null;
    const preferred = item?.colorId;
    return colors.find((c) => c.id === preferred) || colors[0] || null;
  }, [colors, item?.colorId, productData]);

  const lookupProduct = useMemo(() => {
    return (combo?.products || combo?.items || [])?.find(p => String(p.id) === String(item.id));
  }, [combo, item?.id]);

  const previewImage = selectedColor?.thumbnailUrl || item?.imageUrl || lookupProduct?.imageUrl || (productData?.images && productData.images[0]?.imageUrl) || null;
  const fullName = productData?.name || item?.name || lookupProduct?.name || 'Sản phẩm';

  const resolvedPrice = useMemo(() => {
    if (item.displayPrice || item.salePrice || item.price) {
      return item.displayPrice || item.salePrice || item.price;
    }
    if (lookupProduct?.displayPrice || lookupProduct?.salePrice || lookupProduct?.price) {
      return lookupProduct.displayPrice || lookupProduct.salePrice || lookupProduct.price;
    }
    if (productData) {
      const hasActiveSale = productData.isCurrentlyOnSale !== undefined ? productData.isCurrentlyOnSale : (productData.isSale && productData.salePrice);
      return hasActiveSale ? productData.salePrice : (productData.basePrice || productData.price);
    }
    return null;
  }, [item.displayPrice, item.salePrice, item.price, lookupProduct, productData]);

  if (isLoading) {
    return <ChatOutfitItemCardSkeleton role={item.role} optional={item.optional} />;
  }

  const colorCode = selectedColor?.colorCode || item.colorCode || lookupProduct?.colorCode;
  const colorName = selectedColor?.colorName || item.colorName || lookupProduct?.colorName;

  return (
    <a
      href={`/products/${item.id}`}
      className="w-24 shrink-0 block relative group"
    >
      <div className="relative aspect-4/5 overflow-hidden rounded-lg bg-gray-100 border border-gray-100">
        {previewImage ? (
          <img
            src={previewImage}
            alt={fullName}
            className="h-full w-full object-cover transition-transform group-hover:scale-105"
          />
        ) : (
          <div className="h-full w-full flex items-center justify-center bg-gray-50 text-[10px] text-gray-400">
            Không có ảnh
          </div>
        )}
        {item.role && SLOT_ROLE_LABELS[item.role] && (
          <span className="absolute left-1 top-1 rounded bg-black/75 px-1 py-0.25 text-[8px] font-bold text-white uppercase backdrop-blur-xs">
            {SLOT_ROLE_LABELS[item.role]}
          </span>
        )}
        {item.role === 'outer' && item.optional && (
          <span className="absolute right-1 top-1 rounded bg-amber-400 px-1 py-0.25 text-[7px] font-extrabold text-black uppercase">
            T.Chọn
          </span>
        )}
      </div>
      <p
        className="mt-1 text-xs font-semibold text-gray-900 leading-tight line-clamp-2 min-h-7 flex items-center animate-fadeIn"
        title={fullName}
      >
        {fullName}
      </p>

      {/* Swatch and Color Name */}
      {(colorName || item.colorFamily || colorCode) && (
        <div className="mt-0.5 flex items-center gap-1 text-[10px] text-gray-500">
          {colorCode && (
            <span
              className="h-2 w-2 rounded-full border border-gray-200 shrink-0"
              style={{ backgroundColor: colorCode }}
            />
          )}
          <span className="truncate">
            {colorName || COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily || 'Màu sắc'}
          </span>
        </div>
      )}

      {resolvedPrice ? (
        <p className="text-[11px] font-semibold text-gray-700 mt-0.5">
          {formatPrice(resolvedPrice)}
        </p>
      ) : null}
    </a>
  );
};

const ChatWidget = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const [variantSelections, setVariantSelections] = useState({});
  const { messages, isLoading, sendMessage, retryLast, isAuthenticated } = useChatMessages(isOpen);
  const { addToCart } = useCart();
  const messagesEndRef = useRef(null);

  // Tự động cuộn xuống tin nhắn mới nhất
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) scrollToBottom();
  }, [messages, isOpen, isLoading]);

  useEffect(() => {
    const handleOpenChat = () => setIsOpen(true);
    window.addEventListener('open-ai-chat', handleOpenChat);
    return () => {
      window.removeEventListener('open-ai-chat', handleOpenChat);
    };
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!input.trim() || isLoading) return;
    sendMessage(input);
    setInput('');
  };

  const handleSuggestionClick = (question) => {
    if (isLoading) return;
    sendMessage(question);
  };

  const productKey = (product) => `${product.id}-${product.colorId || 'default'}`;

  const handleOutfitForProduct = (product) => {
    if (isLoading || !product?.id) return;
    sendMessage('Sản phẩm này nên mặc với gì?', {
      productId: product.id || product.productId,
      colorId: product.colorId,
      productContext: {
        productId: product.id || product.productId,
        colorId: product.colorId,
        name: product.name || product.productName || 'Sản phẩm',
        gender: product.gender || '',
        category: product.category || product.categoryName || '',
        role: product.role || product.categoryRole || '',
        colorName: product.colorName || '',
        categoryName: product.categoryName || product.category || '',
        categorySlug: product.categorySlug || '',
        categoryRole: product.categoryRole || product.role || '',
        parentCategoryName: product.parentCategoryName || '',
      },
    });
  };

  const handleAddProduct = (product) => {
    const selectedVariantId = variantSelections[productKey(product)];
    if (!selectedVariantId) return;
    addToCart.mutate({ variantId: Number(selectedVariantId), quantity: 1 });
  };

  return (
    <div className="fixed bottom-6 right-6 z-50">
      {/* Chat Window */}
      {isOpen && (
        <div className="fixed sm:absolute inset-x-3 bottom-20 sm:inset-auto sm:bottom-16 sm:right-0 sm:w-[380px] bg-white rounded-2xl shadow-2xl border border-gray-200 flex flex-col overflow-hidden transition-all duration-300 transform origin-bottom-right h-[560px] max-h-[82vh]">
          {/* Header */}
          <div className="bg-black text-white p-4 flex justify-between items-center">
            <div className="flex items-center gap-2">
              <Bot className="w-6 h-6" />
              <div>
                <h3 className="font-bold text-sm">Trợ lý Thời trang AI</h3>
                <p className="text-xs text-gray-300">✨ Powered by Gemini</p>
              </div>
            </div>
            <div className="flex items-center gap-1">
              <button
                type="button"
                className="text-gray-300 hover:text-white transition-colors p-1 rounded-full hover:bg-white/10"
                title="Lịch sử"
              >
                <History className="w-5 h-5" />
              </button>
              <button
                onClick={() => setIsOpen(false)}
                className="text-gray-300 hover:text-white transition-colors p-1 rounded-full hover:bg-white/10"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Messages Area */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-gray-50">
            {!isAuthenticated && (
              <div className="flex items-center gap-2 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>Vui lòng đăng nhập để sử dụng trợ lý thời trang AI.</span>
              </div>
            )}
            {messages.map((msg, index) => (
              <div key={msg.id || `msg-${index}`}>
                {/* Message Bubble */}
                <div
                  className={clsx(
                    'flex gap-3 max-w-[85%]',
                    msg.role === 'user' ? 'ml-auto flex-row-reverse' : 'mr-auto'
                  )}
                >
                  <div className={clsx(
                    'w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0',
                    msg.role === 'user' ? 'bg-gray-200 text-gray-600' : 'bg-black text-white'
                  )}>
                    {msg.role === 'user' ? <User className="w-4 h-4" /> : <Bot className="w-4 h-4" />}
                  </div>
                  {msg.isLoading ? (
                    <div className="p-4 rounded-2xl bg-white border border-gray-200 rounded-tl-sm shadow-sm flex items-center gap-1">
                      <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                      <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                      <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                    </div>
                  ) : (
                    <div className={clsx(
                      'p-3 rounded-2xl text-sm break-words',
                      msg.role === 'user' 
                        ? 'bg-black text-white rounded-tr-sm' 
                        : msg.isError
                          ? 'bg-red-50 border border-red-200 text-red-800 rounded-tl-sm shadow-sm'
                          : 'bg-white border border-gray-200 text-gray-800 rounded-tl-sm shadow-sm'
                    )}>
                      {msg.role === 'user' ? (
                        <p className="whitespace-pre-wrap">{msg.text}</p>
                      ) : (
                        <div className="markdown-body prose prose-sm max-w-none prose-p:leading-relaxed prose-a:text-blue-600">
                          <ReactMarkdown>{msg.text}</ReactMarkdown>
                        </div>
                      )}
                    </div>
                  )}
                </div>

                {/* Search status notification */}
                {msg.searchStatus === 'NEAR_ROLE_FALLBACK' && (
                  <div className="mt-2 ml-11 rounded-lg border border-amber-200 bg-amber-50/75 p-2.5 text-xs text-amber-800 flex items-start gap-1.5 animate-fadeIn">
                    <AlertCircle className="h-4 w-4 text-amber-600 shrink-0 mt-0.5" />
                    <div>
                      <span className="font-semibold block text-amber-900">Gợi ý gần phù hợp</span>
                      {msg.requestedKeyword ? (
                        <span className="text-[11px] block mt-0.5 leading-relaxed">
                          Yêu cầu: <span className="font-semibold">"{msg.requestedKeyword}"</span> - Không có đúng sản phẩm yêu cầu, đang hiển thị lựa chọn gần nhất.
                        </span>
                      ) : (
                        <span className="text-[11px] block mt-0.5 leading-relaxed">Không có đúng sản phẩm yêu cầu, đang hiển thị lựa chọn gần nhất.</span>
                      )}
                    </div>
                  </div>
                )}

                {msg.searchStatus && msg.searchStatus !== 'NEAR_ROLE_FALLBACK' && (
                  <div className="mt-2 ml-11 flex">
                    <span className={clsx(
                      "inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-semibold shadow-2xs border animate-fadeIn",
                      msg.searchStatus === 'TYPE_MATCH' && "bg-emerald-50 border-emerald-200 text-emerald-800",
                      msg.searchStatus === 'PRODUCT_CONTEXT_MATCH' && "bg-blue-50 border-blue-200 text-blue-800",
                      msg.searchStatus === 'NO_MATCH' && "bg-rose-50 border-rose-200 text-rose-800",
                      msg.searchStatus === 'EXTERNAL_CONTEXT_LIMITED' && "bg-purple-50 border-purple-200 text-purple-800"
                    )}>
                      {msg.searchStatus === 'TYPE_MATCH' && 'Sản phẩm phù hợp'}
                      {msg.searchStatus === 'PRODUCT_CONTEXT_MATCH' && 'Theo sản phẩm đang chọn'}
                      {msg.searchStatus === 'NO_MATCH' && 'Chưa tìm thấy sản phẩm phù hợp'}
                      {msg.searchStatus === 'EXTERNAL_CONTEXT_LIMITED' && 'Tư vấn giới hạn'}
                    </span>
                  </div>
                )}

                {/* Recommendations based on priority: outfitCombos > products */}
                {msg.outfitCombos && msg.outfitCombos.length > 0 ? (
                  <div className="mt-2 ml-11 space-y-2">
                    {msg.outfitCombos.map((combo, comboIdx) => {
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
                        comboItems = uniqueProducts(combo.products || combo.items || []);
                      }

                      const occasions = combo.occasion ? combo.occasion.split(',').map(s => s.trim()).filter(Boolean) : [];

                      return (
                        <div key={getComboKey(combo, comboIdx, msg.id)} className="rounded-xl border border-gray-200 bg-white p-3.5 shadow-sm hover:border-gray-300 transition-colors animate-fadeIn">
                          <div className="mb-2 flex flex-wrap items-center gap-1.5 border-b border-gray-100 pb-2">
                            <span className="rounded-full bg-black px-2 py-0.5 text-[10px] font-bold text-white shadow-2xs">
                              {combo.label || combo.outfitType || combo.style || 'Bộ phối'}
                            </span>
                            {combo.score !== undefined && combo.score !== null && combo.score > 0 && (
                              <span className="inline-flex items-center gap-0.5 rounded-full bg-amber-50 border border-amber-200 px-1.5 py-0.25 text-[9px] font-extrabold text-amber-700 shadow-2xs">
                                ✨ Match: {Math.round(combo.score * 100)}%
                              </span>
                            )}
                            {combo.provider && (
                              <span className="rounded bg-gray-100 px-1 py-0.25 text-[8px] font-semibold text-gray-500 uppercase tracking-wider">
                                {combo.provider}
                              </span>
                            )}
                          </div>

                          {/* Description */}
                          {combo.description && (
                            <p className="mb-2 text-xs text-gray-600 leading-relaxed font-normal">{combo.description}</p>
                          )}

                          {/* Reason stylized card */}
                          {combo.reason && (
                            <div className="mb-2 p-2 bg-gray-50 border border-gray-100/50 rounded-lg flex gap-1.5 items-start">
                              <span className="text-xs shrink-0">💡</span>
                              <div>
                                <p className="text-[9px] font-bold text-gray-400 uppercase tracking-wider">Stylist AI khuyên dùng</p>
                                <p className="text-[11px] text-gray-600 mt-0.5 leading-relaxed">{combo.reason}</p>
                              </div>
                            </div>
                          )}

                          {/* Chips & Tags */}
                          {(combo.colorStory || occasions.length > 0) && (
                            <div className="mb-3 flex flex-wrap gap-1.5 items-center">
                              {combo.colorStory && (
                                <span className="inline-flex items-center gap-1 rounded-full bg-indigo-50 border border-indigo-100 px-2 py-0.5 text-[10px] font-semibold text-indigo-700">
                                  🎨 {combo.colorStory}
                                </span>
                              )}
                              {occasions.map((occ) => (
                                <span key={occ} className="inline-flex items-center rounded-full bg-emerald-50 border border-emerald-100 px-2 py-0.5 text-[10px] font-semibold text-emerald-700">
                                  📍 {occ}
                                </span>
                              ))}
                            </div>
                          )}

                          <div className="flex gap-2 overflow-x-auto pb-1">
                            {comboItems.map((item, idx) => (
                              <ChatOutfitItemCard
                                key={buildChatKey("combo-item", msg.id, combo.id || comboIdx, item.role || "role", item.productId || item.id, item.colorId || "no-color", idx)}
                                item={item}
                                combo={combo}
                                comboIdx={comboIdx}
                                idx={idx}
                              />
                            ))}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                ) : msg.products && msg.products.length > 0 ? (
                  <div className="mt-2 ml-11 space-y-2">
                    {uniqueProducts(msg.products).map((product, pIdx) => {
                      const productId = product.id || product.productId;
                      const productName = product.name || product.productName || 'Sản phẩm';
                      const productImg = product.imageUrl || product.primaryImageUrl || product.image || `https://picsum.photos/seed/${productId}/200/200`;
                      const productPrice = product.displayPrice || product.salePrice || product.price || 0;
                      const originalPrice = product.price || product.basePrice || productPrice;
                      const isSale = product.isSale || !!product.salePrice || productPrice < originalPrice;
                      const variants = product.availableVariants || product.variants || [];

                      return (
                        <div key={getProductCardKey(product, pIdx, msg.id, "chat-products")} className="bg-white border border-gray-200 rounded-xl p-2.5 shadow-sm animate-fadeIn">
                          <div className="flex items-center gap-3">
                            {productImg && (
                              <img src={productImg} alt={productName} className="w-14 h-14 rounded-lg object-cover flex-shrink-0" />
                            )}
                            <div className="flex-1 min-w-0">
                              <p className="text-sm font-medium text-gray-900 line-clamp-2 min-h-10 flex items-center" title={productName}>{productName}</p>
                              <div className="flex items-center gap-2 mt-0.5">
                                {isSale ? (
                                  <>
                                    <span className="text-sm font-semibold text-red-600">{formatPrice(productPrice)}</span>
                                    <span className="text-xs text-gray-400 line-through">{formatPrice(originalPrice)}</span>
                                  </>
                                ) : (
                                  <span className="text-sm font-semibold text-gray-900">{formatPrice(productPrice)}</span>
                                )}
                              </div>
                              {product.matchReason && <p className="text-xs text-gray-500 mt-0.5 truncate">{product.matchReason}</p>}
                              {colorLabel(product) && (
                                <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
                                  {product.colorCode && (
                                    <span className="h-3 w-3 rounded-full border border-gray-200" style={{ backgroundColor: product.colorCode }} />
                                  )}
                                  <span className="truncate">{colorLabel(product)}</span>
                                </div>
                              )}
                            </div>
                          </div>
                          <div className="mt-2 flex flex-wrap items-center gap-1.5">
                            {variants.length > 0 && (
                              <select
                                value={variantSelections[productKey(product)] || ''}
                                onChange={(event) => setVariantSelections((prev) => ({
                                  ...prev,
                                  [productKey(product)]: event.target.value,
                                }))}
                                className="h-8 min-w-16 rounded-lg border border-gray-200 bg-white px-2 text-xs text-gray-700"
                              >
                                <option value="">Size</option>
                                {variants.map((variant, vIdx) => {
                                  const variantId = variant.variantId || variant.id;
                                  const sizeName = variant.sizeName || variant.size || 'Size';
                                  return (
                                    <option key={buildChatKey("variant", msg.id, product.id || product.productId, variantId, vIdx)} value={variantId}>{sizeName}</option>
                                  );
                                })}
                              </select>
                            )}
                            <button
                              type="button"
                              disabled={!isAuthenticated || !variantSelections[productKey(product)] || addToCart.isPending}
                              onClick={() => handleAddProduct(product)}
                              className="inline-flex h-8 items-center gap-1 rounded-lg border border-gray-200 px-2 text-xs text-gray-700 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
                              title={isAuthenticated ? 'Thêm vào giỏ hàng' : 'Đăng nhập để thêm vào giỏ'}
                            >
                              <ShoppingCart className="h-3.5 w-3.5" />
                              Thêm
                            </button>
                            <button
                              type="button"
                              onClick={() => handleOutfitForProduct(product)}
                              disabled={isLoading}
                              className="inline-flex h-8 items-center gap-1 rounded-lg border border-gray-200 px-2 text-xs text-gray-700 hover:bg-gray-50 disabled:opacity-50"
                              title="Gợi ý phối đồ với sản phẩm này"
                            >
                              <Sparkles className="h-3.5 w-3.5" />
                              Gợi ý phối đồ với sản phẩm này
                            </button>
                            <a
                              href={`/products/${productId}`}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="inline-flex h-8 items-center gap-1 rounded-lg border border-gray-200 px-2 text-xs text-gray-700 hover:bg-gray-50"
                            >
                              <ExternalLink className="h-3.5 w-3.5" />
                              Xem
                            </a>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                ) : null}

                {(msg.context?.occasionLabel || (msg.styleTips && msg.styleTips.length > 0)) && (
                  <div className="mt-2 ml-11 rounded-xl border border-indigo-100 bg-indigo-50 p-2.5 text-xs text-indigo-900">
                    {msg.context?.occasionLabel && (
                      <p className="mb-1 font-semibold">Dịp phù hợp: {msg.context.occasionLabel}</p>
                    )}
                    {msg.styleTips && msg.styleTips.length > 0 && (
                      <ul className="space-y-1">
                        {msg.styleTips.map((tip, tipIdx) => <li key={buildChatKey("styletip", msg.id, tipIdx)}>- {tip}</li>)}
                      </ul>
                    )}
                  </div>
                )}

                {/* Suggested Questions Chips */}
                {msg.suggestedQuestions && msg.suggestedQuestions.length > 0 && index === messages.length - 1 && (
                  <div className="mt-2 ml-11 flex flex-wrap gap-1.5">
                    {msg.suggestedQuestions.map((q, qIdx) => (
                      <button
                        key={buildChatKey("suggested", msg.id, qIdx)}
                        onClick={() => q === 'Thử lại' ? retryLast() : handleSuggestionClick(q)}
                        disabled={isLoading}
                        className="px-3 py-1.5 text-xs bg-white border border-gray-200 rounded-full text-gray-700 hover:bg-gray-100 hover:border-gray-300 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                      >
                        {q}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
            

            <div ref={messagesEndRef} />
          </div>

          {/* Input Area */}
          <form onSubmit={handleSubmit} className="p-3 bg-white border-t border-gray-200">
            <div className="flex items-center gap-2 bg-gray-100 rounded-full p-1 pr-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={isAuthenticated ? 'Hỏi cách phối đồ, tìm sản phẩm...' : 'Đăng nhập để sử dụng trợ lý AI'}
                className="flex-1 bg-transparent px-4 py-2 text-sm focus:outline-none"
                disabled={!isAuthenticated || isLoading}
              />
              <button
                type="submit"
                disabled={!isAuthenticated || !input.trim() || isLoading}
                className="w-8 h-8 rounded-full bg-black text-white flex items-center justify-center disabled:opacity-50 disabled:cursor-not-allowed transition-opacity hover:bg-gray-800"
              >
                <Send className="w-4 h-4 ml-0.5" />
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Floating Button */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={clsx(
          'w-14 h-14 rounded-full flex items-center justify-center shadow-xl transition-all duration-300 hover:scale-105',
          isOpen ? 'bg-gray-800 text-white rotate-90 scale-0 opacity-0' : 'bg-black text-white rotate-0 scale-100 opacity-100'
        )}
      >
        <Bot className="w-6 h-6" />
      </button>
    </div>
  );
};

export default ChatWidget;
