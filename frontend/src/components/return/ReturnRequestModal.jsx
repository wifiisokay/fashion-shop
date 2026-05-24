import { useMemo, useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import { RETURN_REASON_TYPES, formatPrice } from '../../utils/format';
import Button from '../ui/Button';
import { Minus, Plus, Trash2, Upload, X } from 'lucide-react';
import { toast } from 'sonner';

const MAX_IMAGES = 5;
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

const ReturnRequestModal = ({ orderId, orderItems = [], onClose }) => {
  const [requestType, setRequestType] = useState('RETURN');
  const [reason, setReason] = useState('');
  const [selectedItems, setSelectedItems] = useState({});
  const [images, setImages] = useState([]);
  const fileInputRef = useRef(null);
  const queryClient = useQueryClient();

  const selectedPayload = useMemo(() => Object.entries(selectedItems)
    .filter(([, value]) => value.checked)
    .map(([orderItemId, value]) => ({ orderItemId: Number(orderItemId), quantity: Number(value.quantity) || 1 })), [selectedItems]);

  const mutation = useMutation({
    mutationFn: (formData) => returnApi.createReturn(formData),
    onSuccess: () => {
      toast.success('Đã gửi yêu cầu đổi/trả hoặc khiếu nại');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      onClose();
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Gửi yêu cầu thất bại');
    },
  });

  const toggleItem = (item) => {
    setSelectedItems(prev => ({
      ...prev,
      [item.id]: {
        checked: !prev[item.id]?.checked,
        quantity: prev[item.id]?.quantity || 1,
      },
    }));
  };

  const setItemQuantity = (item, quantity) => {
    const nextQuantity = Math.max(1, Math.min(Number(quantity) || 1, item.quantity || 1));
    setSelectedItems(prev => ({
      ...prev,
      [item.id]: { checked: true, quantity: nextQuantity },
    }));
  };

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
    if (selectedPayload.length === 0) {
      toast.error('Vui lòng chọn ít nhất một sản phẩm');
      return;
    }
    if (!reason.trim()) {
      toast.error('Vui lòng nhập nội dung yêu cầu');
      return;
    }

    const formData = new FormData();
    formData.append('orderId', orderId);
    formData.append('requestType', requestType);
    formData.append('reason', reason.trim());
    formData.append('itemsJson', JSON.stringify(selectedPayload));
    images.forEach(img => formData.append('images', img.file));
    mutation.mutate(formData);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-lg font-bold text-gray-900">Yêu cầu đổi/trả hoặc khiếu nại</h2>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Loại yêu cầu <span className="text-red-500">*</span>
            </label>
            <select
              value={requestType}
              onChange={(e) => setRequestType(e.target.value)}
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black"
            >
              {RETURN_REASON_TYPES.map(type => (
                <option key={type.value} value={type.value}>{type.label}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Sản phẩm cần xử lý <span className="text-red-500">*</span>
            </label>
            <div className="border border-gray-200 rounded-xl divide-y divide-gray-100 overflow-hidden">
              {orderItems.map(item => {
                const selected = selectedItems[item.id]?.checked;
                const quantity = selectedItems[item.id]?.quantity || 1;
                return (
                  <div key={item.id} className="flex gap-3 p-3">
                    <input
                      type="checkbox"
                      checked={!!selected}
                      onChange={() => toggleItem(item)}
                      className="mt-4 h-4 w-4 rounded border-gray-300 text-gray-900 focus:ring-gray-900"
                    />
                    <img src={item.imageUrl || 'https://via.placeholder.com/56'} alt={item.productName} className="w-14 h-14 rounded-lg object-cover border border-gray-200" referrerPolicy="no-referrer" />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-medium text-gray-900 line-clamp-1">{item.productName}</p>
                      <p className="text-xs text-gray-500">{item.colorName || 'Không màu'} / {item.size || 'Không size'} · Đã mua {item.quantity}</p>
                      <p className="text-xs font-medium text-gray-700 mt-1">{formatPrice(item.subtotal || 0)}</p>
                    </div>
                    <div className="flex items-center gap-1 self-center">
                      <button
                        type="button"
                        onClick={() => setItemQuantity(item, quantity - 1)}
                        disabled={!selected}
                        className="p-1 rounded border border-gray-200 disabled:opacity-40"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                      <input
                        type="number"
                        min="1"
                        max={item.quantity || 1}
                        value={quantity}
                        disabled={!selected}
                        onChange={(e) => setItemQuantity(item, e.target.value)}
                        className="w-12 text-center border border-gray-200 rounded p-1 text-sm disabled:opacity-40"
                      />
                      <button
                        type="button"
                        onClick={() => setItemQuantity(item, quantity + 1)}
                        disabled={!selected}
                        className="p-1 rounded border border-gray-200 disabled:opacity-40"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Nội dung yêu cầu <span className="text-red-500">*</span>
            </label>
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={4}
              placeholder="Mô tả vấn đề: sản phẩm lỗi, không đúng mô tả, cần đổi size, cần shop hỗ trợ..."
              className="w-full border border-gray-300 rounded-xl p-3 text-sm focus:ring-black focus:border-black resize-none"
            />
            <p className="text-xs text-gray-400 mt-1">{reason.length}/500 ký tự</p>
          </div>

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

        <div className="flex gap-3 justify-end p-6 border-t border-gray-200">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Hủy</Button>
          <Button onClick={handleSubmit} loading={mutation.isPending} disabled={selectedPayload.length === 0}>Gửi yêu cầu</Button>
        </div>
      </div>
    </div>
  );
};

export default ReturnRequestModal;
