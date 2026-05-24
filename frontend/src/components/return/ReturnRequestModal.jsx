import { useState, useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import Button from '../ui/Button';
import { X, Upload, ImageIcon, Trash2 } from 'lucide-react';
import { toast } from 'sonner';

const MAX_IMAGES = 5;
const MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const ReturnRequestModal = ({ orderId, onClose }) => {
  const [reason, setReason] = useState('');
  const [images, setImages] = useState([]);
  const fileInputRef = useRef(null);
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (formData) => returnApi.createReturn(formData),
    onSuccess: () => {
      toast.success('Đã gửi yêu cầu trả hàng');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      onClose();
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Gửi yêu cầu thất bại');
    },
  });

  const handleFileChange = (e) => {
    const files = Array.from(e.target.files);
    const newImages = [];

    for (const file of files) {
      if (images.length + newImages.length >= MAX_IMAGES) {
        toast.error(`Tối đa ${MAX_IMAGES} ảnh`);
        break;
      }
      if (!ALLOWED_TYPES.includes(file.type)) {
        toast.error(`${file.name}: Chỉ hỗ trợ JPEG, PNG, WebP`);
        continue;
      }
      if (file.size > MAX_FILE_SIZE) {
        toast.error(`${file.name}: Tối đa 5MB`);
        continue;
      }
      newImages.push({ file, preview: URL.createObjectURL(file) });
    }

    setImages(prev => [...prev, ...newImages]);
    e.target.value = '';
  };

  const removeImage = (index) => {
    setImages(prev => {
      URL.revokeObjectURL(prev[index].preview);
      return prev.filter((_, i) => i !== index);
    });
  };

  const handleSubmit = () => {
    if (!reason.trim()) {
      toast.error('Vui lòng nhập lý do trả hàng');
      return;
    }

    const formData = new FormData();
    formData.append('orderId', orderId);
    formData.append('reason', reason.trim());
    images.forEach(img => formData.append('images', img.file));
    mutation.mutate(formData);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-lg font-bold text-gray-900">Yêu cầu trả hàng</h2>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          {/* Reason */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Lý do trả hàng <span className="text-red-500">*</span>
            </label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={4}
              placeholder="Hàng bị lỗi, không đúng mô tả, không vừa size..."
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <p className="text-xs text-gray-400 mt-1">{reason.length}/500 ký tự</p>
          </div>

          {/* Image upload */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Ảnh minh chứng <span className="text-gray-400">(tối đa {MAX_IMAGES} ảnh, mỗi ảnh ≤ 5MB)</span>
            </label>

            <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
              {images.map((img, i) => (
                <div key={i} className="relative aspect-square rounded-lg overflow-hidden border border-gray-200 group">
                  <img src={img.preview} alt="" className="w-full h-full object-cover" />
                  <button
                    onClick={() => removeImage(i)}
                    className="absolute top-1 right-1 p-1 bg-red-500 rounded-full text-white opacity-0 group-hover:opacity-100 transition-opacity"
                  >
                    <Trash2 className="w-3 h-3" />
                  </button>
                </div>
              ))}

              {images.length < MAX_IMAGES && (
                <button
                  type="button"
                  onClick={() => fileInputRef.current?.click()}
                  className="aspect-square border-2 border-dashed border-gray-300 rounded-lg flex flex-col items-center justify-center text-gray-400 hover:border-gray-400 hover:text-gray-500 transition-colors"
                >
                  <Upload className="w-5 h-5 mb-1" />
                  <span className="text-xs">Thêm ảnh</span>
                </button>
              )}
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              className="hidden"
              onChange={handleFileChange}
            />
          </div>
        </div>

        {/* Footer */}
        <div className="flex gap-3 justify-end p-6 border-t border-gray-200">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Hủy</Button>
          <Button onClick={handleSubmit} loading={mutation.isPending}>Gửi yêu cầu</Button>
        </div>
      </div>
    </div>
  );
};

export default ReturnRequestModal;
