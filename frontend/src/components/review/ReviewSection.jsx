import { useState, useCallback } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { reviewApi } from '../../api/reviewApi';
import StarRating from './StarRating';

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
