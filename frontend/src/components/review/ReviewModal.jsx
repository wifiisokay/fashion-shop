import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { reviewApi } from '../../api/reviewApi';
import StarRating from './StarRating';
import { toast } from 'sonner';

/**
 * ReviewModal — Tạo hoặc sửa đánh giá sản phẩm.
 */
export default function ReviewModal({ item, existingReview, onClose }) {
  const isEdit = !!existingReview;
  const [rating, setRating] = useState(existingReview?.rating || 0);
  const [comment, setComment] = useState(existingReview?.comment || '');
  const queryClient = useQueryClient();

  const createMutation = useMutation({
    mutationFn: (data) => reviewApi.createReview(data),
    onSuccess: () => {
      toast.success('Đánh giá thành công!');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      onClose();
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Có lỗi xảy ra'),
  });

  const updateMutation = useMutation({
    mutationFn: (data) => reviewApi.updateReview(existingReview.id, data),
    onSuccess: () => {
      toast.success('Cập nhật đánh giá thành công!');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      onClose();
    },
    onError: (err) => toast.error(err.response?.data?.message || 'Có lỗi xảy ra'),
  });

  const handleSubmit = () => {
    if (rating === 0) { toast.warning('Vui lòng chọn số sao'); return; }
    if (isEdit) {
      updateMutation.mutate({ rating, comment: comment.trim() || null });
    } else {
      createMutation.mutate({ orderItemId: item.id, rating, comment: comment.trim() || null });
    }
  };

  const isLoading = createMutation.isPending || updateMutation.isPending;
  const starLabels = ['', 'Tệ', 'Không hài lòng', 'Bình thường', 'Hài lòng', 'Tuyệt vời'];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md p-6 relative" onClick={e => e.stopPropagation()}>
        <button onClick={onClose} className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 text-xl">✕</button>

        <h3 className="text-lg font-semibold mb-4">{isEdit ? 'Sửa đánh giá' : 'Viết đánh giá'}</h3>

        {/* Product info */}
        <div className="flex items-center gap-3 mb-5 p-3 bg-gray-50 rounded-lg">
          {item.imageUrl && (
            <img src={item.imageUrl} alt={item.productName} className="w-16 h-16 object-cover rounded-md" />
          )}
          <div className="flex-1 min-w-0">
            <p className="font-medium text-sm truncate">{item.productName}</p>
            <p className="text-xs text-gray-500 mt-0.5">
              {item.colorName}{item.size && ` - ${item.size}`}
            </p>
          </div>
        </div>

        {/* Star rating */}
        <div className="text-center mb-5">
          <p className="text-sm text-gray-600 mb-2">Chất lượng sản phẩm</p>
          <StarRating value={rating} onChange={setRating} size={36} />
          {rating > 0 && (
            <p className="text-sm text-amber-600 font-medium mt-1">{starLabels[rating]}</p>
          )}
        </div>

        {/* Comment */}
        <textarea
          className="w-full border border-gray-300 rounded-lg p-3 text-sm resize-none focus:ring-2 focus:ring-primary/30 focus:border-primary outline-none"
          placeholder="Chia sẻ trải nghiệm của bạn (không bắt buộc)"
          value={comment}
          onChange={e => setComment(e.target.value)}
          maxLength={1000}
          rows={4}
        />
        <p className="text-right text-xs text-gray-400 mt-1">{comment.length}/1000</p>

        {/* Actions */}
        <div className="flex gap-3 mt-5">
          <button onClick={onClose} disabled={isLoading}
            className="flex-1 py-2.5 border border-gray-300 rounded-lg text-sm font-medium hover:bg-gray-50 transition-colors">
            Hủy
          </button>
          <button onClick={handleSubmit} disabled={isLoading || rating === 0}
            className="flex-1 py-2.5 bg-primary text-primary-foreground rounded-lg text-sm font-medium hover:opacity-90 transition-opacity disabled:opacity-50">
            {isLoading ? 'Đang gửi...' : isEdit ? 'Cập nhật' : 'Gửi đánh giá'}
          </button>
        </div>
      </div>
    </div>
  );
}
