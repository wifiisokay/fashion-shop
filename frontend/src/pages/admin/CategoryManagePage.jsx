import { useState } from 'react';
import { Plus, Edit, Trash2 } from 'lucide-react';
import Button from '../../components/ui/Button';
import DataTable from '../../components/admin/DataTable';

const MOCK_CATEGORIES = [
  { id: 1, name: 'Áo', description: 'Các loại áo thun, sơ mi, áo len...', productCount: 45 },
  { id: 2, name: 'Quần', description: 'Quần jean, quần tây, quần short...', productCount: 32 },
  { id: 3, name: 'Váy', description: 'Váy liền, chân váy...', productCount: 28 },
  { id: 4, name: 'Áo Khoác', description: 'Áo khoác denim, jacket, blazer...', productCount: 15 },
];

const CategoryManagePage = () => {
  const [categories, setCategories] = useState(MOCK_CATEGORIES);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCat, setEditingCat] = useState(null);
  const [formData, setFormData] = useState({ name: '', description: '' });

  const handleOpenModal = (cat = null) => {
    setEditingCat(cat);
    setFormData(cat ? { name: cat.name, description: cat.description } : { name: '', description: '' });
    setIsModalOpen(true);
  };

  const handleCloseModal = () => {
    setIsModalOpen(false);
    setEditingCat(null);
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (editingCat) {
      setCategories(prev => prev.map(c => c.id === editingCat.id ? { ...c, ...formData } : c));
    } else {
      setCategories(prev => [...prev, { id: Date.now(), ...formData, productCount: 0 }]);
    }
    handleCloseModal();
  };

  const handleDelete = (id) => {
    if (window.confirm('Bạn có chắc chắn muốn xóa danh mục này?')) {
      setCategories(prev => prev.filter(c => c.id !== id));
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium text-gray-500">#{val}</span> },
    { title: 'Tên danh mục', dataIndex: 'name', key: 'name', render: (val) => <span className="font-medium text-gray-900">{val}</span> },
    { title: 'Mô tả', dataIndex: 'description', key: 'description' },
    { title: 'Số sản phẩm', dataIndex: 'productCount', key: 'productCount', render: (val) => <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">{val}</span> },
    { 
      title: 'Hành động', 
      dataIndex: 'id', 
      key: 'action',
      render: (id, record) => (
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="sm" className="p-2 text-blue-600 hover:bg-blue-50" onClick={() => handleOpenModal(record)}>
            <Edit className="w-4 h-4" />
          </Button>
          <Button variant="ghost" size="sm" className="p-2 text-red-600 hover:bg-red-50" onClick={() => handleDelete(id)}>
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      )
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Danh mục</h1>
        <Button className="flex items-center gap-2" onClick={() => handleOpenModal()}>
          <Plus className="w-4 h-4" /> Thêm danh mục
        </Button>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <DataTable 
          columns={columns} 
          data={categories} 
          emptyText="Không có danh mục nào"
        />
      </div>

      {/* Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
          <div className="bg-white rounded-xl shadow-xl w-full max-w-md overflow-hidden">
            <div className="p-4 border-b border-gray-200">
              <h2 className="text-lg font-bold text-gray-900">{editingCat ? 'Chỉnh sửa danh mục' : 'Thêm danh mục mới'}</h2>
            </div>
            <form onSubmit={handleSubmit} className="p-4 space-y-4">
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Tên danh mục *</label>
                <input
                  type="text"
                  required
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
                  placeholder="Nhập tên danh mục"
                />
              </div>
              <div className="space-y-2">
                <label className="block text-sm font-medium text-gray-700">Mô tả</label>
                <textarea
                  rows="3"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full border-gray-300 rounded-lg p-2.5 border focus:ring-black focus:border-black"
                  placeholder="Mô tả ngắn gọn..."
                ></textarea>
              </div>
              <div className="pt-4 flex justify-end gap-3">
                <Button type="button" variant="outline" onClick={handleCloseModal}>Hủy</Button>
                <Button type="submit">{editingCat ? 'Cập nhật' : 'Thêm mới'}</Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default CategoryManagePage;
