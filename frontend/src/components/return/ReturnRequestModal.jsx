import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { returnApi } from '../../api/returnApi';
import { formatPrice } from '../../utils/format';
import Button from '../ui/Button';
import { X, Upload } from 'lucide-react';
import { toast } from 'sonner';
import { clsx } from 'clsx';

const MAX_IMAGES = 5;

const ReturnRequestModal = ({ orderId, orderItems = [], onClose }) => {
  const [reason, setReason] = useState('');
  const [imageUrls, setImageUrls] = useState([]);
  const [isUploading, setIsUploading] = useState(false);
  const [selectedItems, setSelectedItems] = useState({}); // { [orderItemId]: quantity }
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: (payload) => returnApi.createReturn(payload),
    onSuccess: () => {
      toast.success('Đã gửi yêu cầu trả hàng');
      queryClient.invalidateQueries({ queryKey: ['myOrders'] });
      onClose();
    },
    onError: (err) => {
      toast.error(err.response?.data?.message || 'Gửi yêu cầu thất bại');
    },
  });

  const handleToggleItem = (itemId) => {
    setSelectedItems((prev) => {
      const next = { ...prev };
      if (next[itemId]) {
        delete next[itemId];
      } else {
        next[itemId] = 1;
      }
      return next;
    });
  };

  const handleQuantityChange = (itemId, qty) => {
    setSelectedItems((prev) => ({
      ...prev,
      [itemId]: parseInt(qty),
    }));
  };

  const handleFileChange = async (e) => {
    const files = Array.from(e.target.files);
    if (!files.length) return;

    if (imageUrls.length + files.length > MAX_IMAGES) {
      toast.error(`Chỉ được tải lên tối đa ${MAX_IMAGES} ảnh minh chứng.`);
      return;
    }

    setIsUploading(true);
    try {
      const uploadedUrls = [];
      for (const file of files) {
        if (file.size > 5 * 1024 * 1024) {
          toast.error(`File ${file.name} vượt quá dung lượng 5MB cho phép.`);
          continue;
        }
        if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
          toast.error(`File ${file.name} không đúng định dạng ảnh cho phép.`);
          continue;
        }

        const res = await returnApi.uploadEvidence(file);
        if (res.data?.data?.url) {
          uploadedUrls.push(res.data.data.url);
        }
      }
      setImageUrls((prev) => [...prev, ...uploadedUrls]);
      toast.success('Đã tải ảnh lên thành công.');
    } catch (err) {
      toast.error('Lỗi khi tải ảnh lên Cloudinary.');
      console.error(err);
    } finally {
      setIsUploading(false);
      e.target.value = '';
    }
  };

  const handleSubmit = () => {
    if (!reason.trim()) {
      toast.error('Vui lòng nhập nội dung yêu cầu');
      return;
    }

    const payloadItems = Object.entries(selectedItems).map(([orderItemId, quantity]) => ({
      orderItemId: parseInt(orderItemId),
      quantity,
    }));

    if (payloadItems.length === 0) {
      toast.error('Vui lòng chọn ít nhất 1 sản phẩm để trả hàng');
      return;
    }

    mutation.mutate({
      orderId,
      reason: reason.trim(),
      evidenceImages: imageUrls,
      items: payloadItems,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div className="bg-white rounded-2xl shadow-xl max-w-2xl w-full max-h-[90vh] overflow-y-auto">
        <div className="flex items-center justify-between p-6 border-b border-gray-200">
          <h2 className="text-lg font-bold text-gray-900">Yêu cầu trả hàng</h2>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-full transition-colors">
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>

        <div className="p-6 space-y-5">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Sản phẩm trong đơn
            </label>
            <div className="border border-gray-200 rounded-xl divide-y divide-gray-100 overflow-hidden">
              {orderItems.map(item => {
                const isSelected = selectedItems[item.id] !== undefined;
                return (
                  <div key={item.id} className={clsx("flex gap-3 p-3 transition-colors duration-150", isSelected ? "bg-slate-50/50" : "bg-white")}>
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => handleToggleItem(item.id)}
                      className="w-4 h-4 rounded border-gray-300 text-black focus:ring-black self-center mr-1 cursor-pointer"
                    />
                    <img src={item.imageUrl || 'https://via.placeholder.com/56'} alt={item.productName} className="w-14 h-14 rounded-lg object-cover border border-gray-200 flex-shrink-0" referrerPolicy="no-referrer" />
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-gray-900 line-clamp-1">{item.productName}</p>
                      <p className="text-xs text-gray-500 mt-0.5">{item.colorName || 'Không màu'} / {item.size || 'Không size'} · Đã mua {item.quantity}</p>
                      <p className="text-xs font-semibold text-gray-700 mt-1">{formatPrice(item.unitPrice || 0)} x {item.quantity}</p>
                    </div>
                    {isSelected && (
                      <div className="flex items-center gap-1.5 self-center">
                        <span className="text-xs text-gray-500 font-semibold">SL trả:</span>
                        <select
                          value={selectedItems[item.id]}
                          onChange={(e) => handleQuantityChange(item.id, e.target.value)}
                          className="border border-gray-300 rounded-lg text-xs font-semibold px-2.5 py-1 focus:ring-black focus:border-black cursor-pointer bg-white"
                        >
                          {Array.from({ length: item.quantity }, (_, i) => i + 1).map((val) => (
                            <option key={val} value={val}>
                              {val}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
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
              Ảnh minh chứng <span className="text-gray-400">(tối đa {MAX_IMAGES} ảnh)</span>
            </label>
            
            {imageUrls.length > 0 && (
              <div className="flex flex-wrap gap-3 mb-3">
                {imageUrls.map((url, index) => (
                  <div key={index} className="relative w-20 h-20 group border border-gray-200 rounded-xl overflow-hidden shadow-sm">
                    <img src={url} alt={`Minh chứng ${index + 1}`} className="w-full h-full object-cover" />
                    <button
                      type="button"
                      onClick={() => setImageUrls(imageUrls.filter((_, i) => i !== index))}
                      className="absolute inset-0 bg-black/50 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity duration-200 text-white font-medium text-xs"
                    >
                      Xóa
                    </button>
                  </div>
                ))}
              </div>
            )}

            {imageUrls.length < MAX_IMAGES && (
              <label className={clsx(
                "inline-flex items-center justify-center gap-2 border-2 border-dashed border-gray-200 rounded-xl px-4 py-3 cursor-pointer text-sm font-medium text-gray-600 hover:bg-gray-50 transition-all duration-200",
                isUploading && "pointer-events-none opacity-50"
              )}>
                <Upload className="w-4 h-4 text-gray-500" />
                <span>{isUploading ? 'Đang tải lên...' : 'Tải ảnh minh chứng lên'}</span>
                <input
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp,image/gif"
                  onChange={handleFileChange}
                  className="hidden"
                />
              </label>
            )}
          </div>
        </div>

        <div className="flex gap-3 justify-end p-6 border-t border-gray-200">
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>Hủy</Button>
          <Button onClick={handleSubmit} loading={mutation.isPending} disabled={mutation.isPending || !reason.trim() || Object.keys(selectedItems).length === 0}>Gửi yêu cầu</Button>
        </div>
      </div>
    </div>
  );
};

export default ReturnRequestModal;
