import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Save, Plus, Trash2, Upload } from 'lucide-react';
import Button from '../../components/ui/Button';

const ProductFormPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const id = searchParams.get('id');
  const isEdit = !!id;

  const [formData, setFormData] = useState({
    name: '',
    category: '',
    price: '',
    salePrice: '',
    saleStartDate: '',
    saleEndDate: '',
    description: '',
    imageUrl: '',
    variations: [{ id: Date.now(), color: '', size: '', stock: '', image: '' }]
  });

  useEffect(() => {
    if (isEdit) {
      // Mock fetch product data
      setFormData({
        name: 'Áo Thun Basic Nam',
        category: 'Áo',
        price: '250000',
        salePrice: '199000',
        saleStartDate: '2026-03-01T00:00',
        saleEndDate: '2026-04-30T23:59',
        description: 'Áo thun cotton 100% thoáng mát.',
        imageUrl: 'https://picsum.photos/seed/1/400/600',
        variations: [
          { id: 1, color: 'Đen', size: 'M', stock: '50', image: 'https://picsum.photos/seed/black/800/1000' },
          { id: 2, color: 'Đen', size: 'L', stock: '50', image: 'https://picsum.photos/seed/black/800/1000' },
          { id: 3, color: 'Trắng', size: 'M', stock: '50', image: 'https://picsum.photos/seed/white/800/1000' },
        ]
      });
    }
  }, [isEdit]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({ ...prev, [name]: value }));
  };

  const handleAddVariation = () => {
    setFormData(prev => ({
      ...prev,
      variations: [...prev.variations, { id: Date.now(), color: '', size: '', stock: '', image: '' }]
    }));
  };

  const handleRemoveVariation = (idToRemove) => {
    setFormData(prev => ({
      ...prev,
      variations: prev.variations.filter(v => v.id !== idToRemove)
    }));
  };

  const handleVariationChange = (id, field, value) => {
    setFormData(prev => ({
      ...prev,
      variations: prev.variations.map(v => 
        v.id === id ? { ...v, [field]: value } : v
      )
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    alert(isEdit ? 'Cập nhật sản phẩm thành công!' : 'Thêm sản phẩm thành công!');
    navigate('/admin/products');
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="flex items-center gap-4">
        <Button variant="ghost" className="p-2" onClick={() => navigate('/admin/products')}>
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-2xl font-bold text-gray-900">
          {isEdit ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}
        </h1>
      </div>

      <form onSubmit={handleSubmit} className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700">Tên sản phẩm *</label>
            <input
              type="text"
              name="name"
              required
              value={formData.name}
              onChange={handleChange}
              className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
              placeholder="Nhập tên sản phẩm"
            />
          </div>
          
          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700">Danh mục *</label>
            <select
              name="category"
              required
              value={formData.category}
              onChange={handleChange}
              className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
            >
              <option value="">Chọn danh mục</option>
              <option value="Áo">Áo</option>
              <option value="Quần">Quần</option>
              <option value="Váy">Váy</option>
              <option value="Áo Khoác">Áo Khoác</option>
            </select>
          </div>

          <div className="space-y-2">
            <label className="block text-sm font-medium text-gray-700">Giá gốc (VNĐ) *</label>
            <input
              type="number"
              name="price"
              required
              min="0"
              value={formData.price}
              onChange={handleChange}
              className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
              placeholder="0"
            />
          </div>
        </div>

        <div className="space-y-4 border-t border-gray-200 pt-6">
          <h3 className="text-base font-semibold text-gray-900">Khuyến mãi (Tùy chọn)</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">Giá khuyến mãi (VNĐ)</label>
              <input
                type="number"
                name="salePrice"
                min="0"
                value={formData.salePrice}
                onChange={handleChange}
                className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
                placeholder="0"
              />
            </div>
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">Từ ngày</label>
              <input
                type="datetime-local"
                name="saleStartDate"
                value={formData.saleStartDate}
                onChange={handleChange}
                className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
              />
            </div>
            <div className="space-y-2">
              <label className="block text-sm font-medium text-gray-700">Đến ngày</label>
              <input
                type="datetime-local"
                name="saleEndDate"
                value={formData.saleEndDate}
                onChange={handleChange}
                className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
              />
            </div>
          </div>
        </div>

        <div className="space-y-4 border-t border-gray-200 pt-6">
          <div className="flex items-center justify-between">
            <label className="block text-base font-semibold text-gray-900">Phân loại sản phẩm (Màu sắc & Kích thước)</label>
            <Button type="button" variant="outline" size="sm" onClick={handleAddVariation} className="flex items-center gap-2">
              <Plus className="w-4 h-4" /> Thêm phân loại
            </Button>
          </div>
          
          {formData.variations.length === 0 ? (
            <div className="text-center py-4 text-sm text-gray-500 bg-gray-50 rounded-lg border border-dashed border-gray-300">
              Chưa có phân loại nào. Hãy thêm phân loại để quản lý tồn kho chi tiết.
            </div>
          ) : (
            <div className="space-y-3">
              {formData.variations.map((variation, index) => (
                <div key={variation.id} className="flex items-start sm:items-center gap-3 bg-gray-50 p-3 rounded-lg border border-gray-200">
                  <div className="grid grid-cols-1 sm:grid-cols-4 gap-3 flex-1">
                    <div className="flex items-center gap-2">
                      <input 
                        type="file" 
                        accept="image/*" 
                        className="hidden" 
                        id={`upload-${variation.id}`}
                        onChange={(e) => {
                          if (e.target.files && e.target.files[0]) {
                            const url = URL.createObjectURL(e.target.files[0]);
                            handleVariationChange(variation.id, 'image', url);
                          }
                        }}
                      />
                      <label 
                        htmlFor={`upload-${variation.id}`} 
                        className="cursor-pointer shrink-0 flex items-center justify-center w-10 h-10 border border-dashed border-gray-400 rounded-lg hover:bg-gray-100 overflow-hidden"
                        title="Tải ảnh cho phân loại này"
                      >
                        {variation.image ? (
                          <img src={variation.image} alt="var" className="w-full h-full object-cover" />
                        ) : (
                          <Upload className="w-4 h-4 text-gray-500" />
                        )}
                      </label>
                      <input
                        type="text"
                        required
                        placeholder="Màu (VD: Đen)"
                        value={variation.color}
                        onChange={(e) => handleVariationChange(variation.id, 'color', e.target.value)}
                        className="w-full border-gray-300 rounded-lg p-2 text-sm border focus:ring-black focus:border-black"
                      />
                    </div>
                    <div>
                      <input
                        type="text"
                        required
                        placeholder="Kích thước (VD: S, M, L)"
                        value={variation.size}
                        onChange={(e) => handleVariationChange(variation.id, 'size', e.target.value)}
                        className="w-full border-gray-300 rounded-lg p-2 text-sm border focus:ring-black focus:border-black"
                      />
                    </div>
                    <div>
                      <input
                        type="number"
                        required
                        min="0"
                        placeholder="Số lượng tồn kho"
                        value={variation.stock}
                        onChange={(e) => handleVariationChange(variation.id, 'stock', e.target.value)}
                        className="w-full border-gray-300 rounded-lg p-2 text-sm border focus:ring-black focus:border-black"
                      />
                    </div>
                  </div>
                  <Button 
                    type="button" 
                    variant="ghost" 
                    className="p-2 text-red-600 hover:bg-red-100 shrink-0"
                    onClick={() => handleRemoveVariation(variation.id)}
                  >
                    <Trash2 className="w-4 h-4" />
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="space-y-2 border-t border-gray-200 pt-6">
          <label className="block text-sm font-medium text-gray-700">URL Hình ảnh</label>
          <input
            type="url"
            name="imageUrl"
            value={formData.imageUrl}
            onChange={handleChange}
            className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
            placeholder="https://example.com/image.jpg"
          />
        </div>

        <div className="space-y-2">
          <label className="block text-sm font-medium text-gray-700">Mô tả sản phẩm</label>
          <textarea
            name="description"
            rows="4"
            value={formData.description}
            onChange={handleChange}
            className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
            placeholder="Nhập mô tả chi tiết..."
          ></textarea>
        </div>

        <div className="pt-4 flex justify-end gap-3 border-t border-gray-200">
          <Button type="button" variant="outline" onClick={() => navigate('/admin/products')}>
            Hủy
          </Button>
          <Button type="submit" className="flex items-center gap-2">
            <Save className="w-4 h-4" /> {isEdit ? 'Cập nhật' : 'Lưu sản phẩm'}
          </Button>
        </div>
      </form>
    </div>
  );
};

export default ProductFormPage;
