import { useState, useRef, useEffect } from 'react';
import { X, Send, Bot, User, ExternalLink, History, AlertCircle } from 'lucide-react';
import { useChatMessages } from '../../hooks/useChatMessages';
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

const uniqueProducts = (items = []) => {
  const seen = new Set();
  return items.filter((item) => {
    if (!item?.id) return false;
    const key = String(item.id);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
};

const colorLabel = (item) => {
  if (!item?.colorName && !item?.colorFamily) return null;
  return [item.colorName, COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily].filter(Boolean).join(' / ');
};

const ChatWidget = () => {
  const [isOpen, setIsOpen] = useState(false);
  const [input, setInput] = useState('');
  const { messages, isLoading, sendMessage, retryLast, isAuthenticated } = useChatMessages(isOpen);
  const messagesEndRef = useRef(null);

  // Tự động cuộn xuống tin nhắn mới nhất
  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen) scrollToBottom();
  }, [messages, isOpen, isLoading]);

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
                <span>Chế độ khách: bạn vẫn có thể hỏi sản phẩm/phối đồ, nhưng cần đăng nhập để xem đơn hàng.</span>
              </div>
            )}
            {messages.map((msg, index) => (
              <div key={index}>
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
                </div>

                {/* Product Cards */}
                {msg.products && msg.products.length > 0 && (
                  <div className="mt-2 ml-11 space-y-2">
                    {uniqueProducts(msg.products).map((product) => (
                      <a
                        key={product.id}
                        href={`/products/${product.id}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center gap-3 p-2.5 bg-white border border-gray-200 rounded-xl hover:border-gray-300 hover:shadow-sm transition-all group"
                      >
                        {product.imageUrl && (
                          <img
                            src={product.imageUrl}
                            alt={product.name}
                            className="w-14 h-14 rounded-lg object-cover flex-shrink-0"
                          />
                        )}
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium text-gray-900 truncate group-hover:text-blue-600 transition-colors">
                            {product.name}
                          </p>
                          <div className="flex items-center gap-2 mt-0.5">
                            {product.salePrice ? (
                              <>
                                <span className="text-sm font-semibold text-red-600">{formatPrice(product.displayPrice || product.salePrice)}</span>
                                <span className="text-xs text-gray-400 line-through">{formatPrice(product.price)}</span>
                              </>
                            ) : (
                              <span className="text-sm font-semibold text-gray-900">{formatPrice(product.displayPrice || product.price)}</span>
                            )}
                          </div>
                          {product.matchReason && (
                            <p className="text-xs text-gray-500 mt-0.5 truncate">{product.matchReason}</p>
                          )}
                          {colorLabel(product) && (
                            <div className="mt-1 flex items-center gap-1.5 text-xs text-gray-500">
                              {product.colorCode && (
                                <span className="h-3 w-3 rounded-full border border-gray-200" style={{ backgroundColor: product.colorCode }} />
                              )}
                              <span className="truncate">{colorLabel(product)}</span>
                            </div>
                          )}
                        </div>
                        <ExternalLink className="w-4 h-4 text-gray-400 group-hover:text-blue-500 flex-shrink-0" />
                      </a>
                    ))}
                  </div>
                )}

                {/* Outfit Combos */}
                {msg.outfitCombos && msg.outfitCombos.length > 0 && (
                  <div className="mt-2 ml-11 space-y-2">
                    {msg.outfitCombos.map((combo, comboIdx) => (
                      <div key={comboIdx} className="rounded-xl border border-gray-200 bg-white p-3 shadow-sm">
                        <div className="mb-2 flex items-center justify-between gap-2">
                          <span className="rounded-full bg-gray-900 px-2 py-0.5 text-xs font-medium text-white">
                            {combo.label || combo.outfitType || combo.style}
                          </span>
                        </div>
                        {(combo.description || combo.reason) && <p className="mb-2 text-xs text-gray-600">{combo.description || combo.reason}</p>}
                        {(combo.colorStory || combo.occasion) && (
                          <div className="mb-2 flex flex-wrap gap-1.5 text-[11px] text-gray-600">
                            {combo.colorStory && <span className="rounded-full bg-gray-100 px-2 py-0.5">{combo.colorStory}</span>}
                            {combo.occasion && <span className="rounded-full bg-blue-50 px-2 py-0.5 text-blue-700">{combo.occasion}</span>}
                          </div>
                        )}
                        <div className="flex gap-2 overflow-x-auto pb-1">
                          {uniqueProducts(combo.products || combo.items || []).map((item) => (
                            <a key={`${comboIdx}-${item.id}`} href={`/products/${item.id}`} className="w-24 shrink-0">
                              {item.imageUrl && <img src={item.imageUrl} alt={item.name} className="h-28 w-24 rounded-lg object-cover" />}
                              <p className="mt-1 truncate text-xs font-medium text-gray-900">{item.name}</p>
                              {colorLabel(item) && (
                                <div className="mt-0.5 flex items-center gap-1 text-[11px] text-gray-500">
                                  {item.colorCode && (
                                    <span className="h-2.5 w-2.5 rounded-full border border-gray-200" style={{ backgroundColor: item.colorCode }} />
                                  )}
                                  <span className="truncate">{item.colorName || COLOR_FAMILY_LABELS[item.colorFamily] || item.colorFamily}</span>
                                </div>
                              )}
                              <p className="text-xs text-gray-600">{formatPrice(item.displayPrice || item.salePrice || item.price)}</p>
                            </a>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                {/* Suggested Questions Chips */}
                {msg.suggestedQuestions && msg.suggestedQuestions.length > 0 && index === messages.length - 1 && (
                  <div className="mt-2 ml-11 flex flex-wrap gap-1.5">
                    {msg.suggestedQuestions.map((q, qIdx) => (
                      <button
                        key={qIdx}
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
            
            {isLoading && (
              <div className="flex gap-3 max-w-[85%] mr-auto">
                <div className="w-8 h-8 rounded-full bg-black text-white flex items-center justify-center flex-shrink-0">
                  <Bot className="w-4 h-4" />
                </div>
                <div className="p-4 rounded-2xl bg-white border border-gray-200 rounded-tl-sm shadow-sm flex items-center gap-1">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Input Area */}
          <form onSubmit={handleSubmit} className="p-3 bg-white border-t border-gray-200">
            <div className="flex items-center gap-2 bg-gray-100 rounded-full p-1 pr-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder="Hỏi cách phối đồ, tìm sản phẩm..."
                className="flex-1 bg-transparent px-4 py-2 text-sm focus:outline-none"
                disabled={isLoading}
              />
              <button
                type="submit"
                disabled={!input.trim() || isLoading}
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
