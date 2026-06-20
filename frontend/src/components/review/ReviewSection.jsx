import { useState, useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewApi } from '../../api/reviewApi';
import StarRating from './StarRating';
import { useAiInsight } from '../../hooks/useAiInsight';
import { Sparkles, CheckCircle2, AlertTriangle, Info } from 'lucide-react';

/**
 * ReviewSection — Hiển thị review trên ProductDetailPage.
 * Sử dụng single page query + accumulate reviews client-side.
 */
export default function ReviewSection({ productId }) {
  const [filterRating, setFilterRating] = useState(null);
  const [sort, setSort] = useState('newest');
  const [page, setPage] = useState(0);
  const [accumulated, setAccumulated] = useState([]);
  const queryClient = useQueryClient();

  // AI Insight Summary
  const { data: aiInsight, isLoading: isAiLoading } = useAiInsight(productId);

  // Stats
  const { data: stats } = useQuery({
    queryKey: ['review-stats', productId],
    queryFn: () => reviewApi.getReviewStats(productId).then(r => r.data.data),
    enabled: !!productId,
  });

  // Current page reviews
  const { data: currentPage, isLoading, isFetching } = useQuery({
    queryKey: ['product-reviews', productId, filterRating, sort, page],
    queryFn: async () => {
      const res = await reviewApi.getProductReviews(productId, {
        rating: filterRating, sort, page, size: 5
      });
      return res.data.data;
    },
    enabled: !!productId,
  });

  // Derive display reviews: accumulated (from load more) + current page
  const displayReviews = page === 0
    ? (currentPage?.content || [])
    : [...accumulated, ...(currentPage?.content || [])];

  // Filter/sort reset
  const resetAndFetch = useCallback((newFilter, newSort) => {
    setAccumulated([]);
    setPage(0);
    if (newFilter !== undefined) setFilterRating(newFilter);
    if (newSort !== undefined) setSort(newSort);
  }, []);

  const handleFilterChange = (r) => {
    resetAndFetch(r === filterRating ? null : r, undefined);
  };

  const handleLoadMore = () => {
    // Save current reviews before fetching next page
    setAccumulated(displayReviews);
    setPage(p => p + 1);
  };

  if (!stats || stats.totalReviews === 0) {
    return (
      <div className="mt-10 border-t pt-8">
        <h2 className="text-xl font-semibold mb-4">Đánh giá sản phẩm</h2>
        <p className="text-gray-500 text-sm">Chưa có đánh giá nào cho sản phẩm này</p>
      </div>
    );
  }

  return (
    <div className="mt-10 border-t pt-8">
      <h2 className="text-xl font-semibold mb-6">Đánh giá sản phẩm</h2>

      {/* Stats */}
      <div className="flex flex-col sm:flex-row gap-6 mb-6 p-5 bg-amber-50/50 rounded-xl border border-amber-100">
        <div className="flex flex-col items-center justify-center sm:min-w-[140px]">
          <span className="text-4xl font-bold text-amber-600">{stats.avgRating}</span>
          <span className="text-sm text-gray-500">trên 5</span>
          <StarRating value={Math.round(stats.avgRating)} size={16} />
          <span className="text-xs text-gray-400 mt-1">{stats.totalReviews} đánh giá</span>
        </div>
        <div className="flex-1 space-y-1.5">
          {[5, 4, 3, 2, 1].map((star) => {
            const count = stats.breakdown?.[star] || 0;
            const pct = stats.totalReviews > 0 ? (count / stats.totalReviews) * 100 : 0;
            return (
              <div key={star} className="flex items-center gap-2 text-sm">
                <span className="w-10 text-right text-gray-600">{star} ★</span>
                <div className="flex-1 h-2.5 bg-gray-200 rounded-full overflow-hidden">
                  <div className="h-full bg-amber-400 rounded-full transition-all duration-300"
                    style={{ width: `${pct}%` }} />
                </div>
                <span className="w-8 text-gray-500 text-xs">{count}</span>
              </div>
            );
          })}
        </div>
      </div>

      {/* AI Review Summary */}
      {isAiLoading && (
        <div className="mb-6 rounded-2xl border border-indigo-100 bg-indigo-50/10 p-5 animate-pulse">
          <div className="flex items-center gap-2 mb-4">
            <div className="h-5 w-5 bg-indigo-200 rounded-full" />
            <div className="h-5 w-48 bg-indigo-200 rounded" />
          </div>
          <div className="h-4 bg-indigo-150 rounded w-full mb-3" />
          <div className="h-4 bg-indigo-150 rounded w-5/6 mb-4" />
          <div className="grid md:grid-cols-2 gap-4">
            <div className="space-y-2">
              <div className="h-3 w-1/3 bg-green-200 rounded" />
              <div className="h-3.5 w-3/4 bg-gray-200 rounded" />
              <div className="h-3.5 w-2/3 bg-gray-200 rounded" />
            </div>
            <div className="space-y-2">
              <div className="h-3 w-1/3 bg-amber-200 rounded" />
              <div className="h-3.5 w-3/4 bg-gray-200 rounded" />
              <div className="h-3.5 w-2/3 bg-gray-200 rounded" />
            </div>
          </div>
        </div>
      )}

      {!isAiLoading && aiInsight?.hasReviewSummary && aiInsight?.reviewSummary && (
        <div className="mb-6 rounded-2xl border border-indigo-100 bg-linear-to-r from-violet-50/30 to-indigo-50/30 p-5 sm:p-6 hover:shadow-xs transition-all duration-300">
          <div className="flex items-center justify-between gap-2 mb-4 border-b border-indigo-100/50 pb-3">
            <div className="flex items-center gap-2">
              <Sparkles className="w-5 h-5 text-indigo-600 animate-pulse shrink-0" />
              <span className="font-bold text-sm sm:text-base text-indigo-950">
                Tóm tắt đánh giá bởi Gemini AI
              </span>
            </div>
            <span className="text-[10px] bg-indigo-100/80 text-indigo-700 px-2 py-0.5 rounded-full font-bold uppercase tracking-wider shrink-0">
              AI Generated
            </span>
          </div>

          {aiInsight.reviewSummary.overall && (
            <p className="text-sm text-gray-700 leading-relaxed mb-4 p-3 bg-white/60 border border-indigo-50/50 rounded-xl italic">
              "{aiInsight.reviewSummary.overall}"
            </p>
          )}

          <div className="grid md:grid-cols-2 gap-4 mb-4">
            {/* Pros */}
            {aiInsight.reviewSummary.pros && aiInsight.reviewSummary.pros.length > 0 && (
              <div className="p-3.5 bg-green-50/30 border border-green-100/30 rounded-xl">
                <h4 className="text-xs font-bold text-green-800 uppercase tracking-wider mb-2.5 flex items-center gap-1.5">
                  <CheckCircle2 className="w-4 h-4 text-green-600 shrink-0" />
                  Ưu điểm nổi bật
                </h4>
                <ul className="space-y-2">
                  {aiInsight.reviewSummary.pros.map((pro, index) => (
                    <li key={index} className="flex items-start gap-2 text-xs text-gray-700 leading-relaxed">
                      <span className="text-green-500 shrink-0 mt-0.5">•</span>
                      <span>{pro}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}

            {/* Cons */}
            {aiInsight.reviewSummary.cons && aiInsight.reviewSummary.cons.length > 0 && (
              <div className="p-3.5 bg-amber-50/30 border border-amber-100/30 rounded-xl">
                <h4 className="text-xs font-bold text-amber-800 uppercase tracking-wider mb-2.5 flex items-center gap-1.5">
                  <AlertTriangle className="w-4 h-4 text-amber-600 shrink-0" />
                  Điểm cần lưu ý
                </h4>
                <ul className="space-y-2">
                  {aiInsight.reviewSummary.cons.map((con, index) => (
                    <li key={index} className="flex items-start gap-2 text-xs text-gray-700 leading-relaxed">
                      <span className="text-amber-500 shrink-0 mt-0.5">•</span>
                      <span>{con}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </div>

          {aiInsight.reviewSummary.fitNote && (
            <div className="flex items-start gap-2 p-3 bg-indigo-50/30 border border-indigo-100/30 rounded-xl text-xs text-indigo-900 leading-relaxed">
              <Info className="w-4 h-4 text-indigo-600 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold">Độ chuẩn xác kích cỡ (Fit Guide): </span>
                <span>{aiInsight.reviewSummary.fitNote}</span>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Filters */}
      <div className="flex flex-wrap gap-2 mb-4">
        <button onClick={() => handleFilterChange(null)}
          className={`px-3 py-1.5 text-sm rounded-full border transition-colors ${
            !filterRating ? 'bg-primary text-primary-foreground border-primary' : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'
          }`}>
          Tất cả
        </button>
        {[5, 4, 3, 2, 1].map((star) => (
          <button key={star} onClick={() => handleFilterChange(star)}
            className={`px-3 py-1.5 text-sm rounded-full border transition-colors ${
              filterRating === star ? 'bg-primary text-primary-foreground border-primary' : 'bg-white text-gray-600 border-gray-300 hover:border-gray-400'
            }`}>
            {star} ★ ({stats.breakdown?.[star] || 0})
          </button>
        ))}
      </div>

      {/* Sort */}
      <div className="flex justify-end mb-4">
        <select value={sort} onChange={(e) => resetAndFetch(undefined, e.target.value)}
          className="text-sm border border-gray-300 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-primary/30">
          <option value="newest">Mới nhất</option>
          <option value="highest">Cao nhất</option>
          <option value="lowest">Thấp nhất</option>
        </select>
      </div>

      {/* Review list */}
      <div className="space-y-4">
        {displayReviews.map((review) => (
          <div key={review.id} className="p-4 border border-gray-100 rounded-lg hover:bg-gray-50/50 transition-colors">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-9 h-9 rounded-full bg-primary/10 text-primary flex items-center justify-center text-sm font-semibold">
                {review.customerName?.charAt(0)?.toUpperCase() || '?'}
              </div>
              <div className="flex-1">
                <span className="text-sm font-medium">{review.customerName}</span>
                <div className="flex items-center gap-2 mt-0.5">
                  <span className="text-xs text-gray-400">
                    {new Date(review.createdAt).toLocaleDateString('vi-VN')}
                  </span>
                  <span className="text-[10px] font-medium text-green-700 bg-green-50 px-1.5 py-0.5 rounded flex items-center gap-1">
                    ✓ Đã mua hàng
                  </span>
                </div>
              </div>
            </div>
            <div className="flex items-center gap-3 mb-2">
              <StarRating value={review.rating} size={14} />
              {(review.colorName || review.size) && (
                <span className="text-xs text-gray-500 border-l pl-3 border-gray-200">
                  Phân loại: {review.colorName}{review.size && ` - ${review.size}`}
                </span>
              )}
            </div>
            {review.comment && (
              <p className="text-sm text-gray-700 mt-2 leading-relaxed">{review.comment}</p>
            )}
          </div>
        ))}
      </div>

      {/* Load more */}
      {currentPage && !currentPage.last && (
        <div className="text-center mt-6">
          <button onClick={handleLoadMore} disabled={isFetching}
            className="px-6 py-2 border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors disabled:opacity-50">
            {isFetching ? 'Đang tải...' : 'Xem thêm đánh giá'}
          </button>
        </div>
      )}
    </div>
  );
}
