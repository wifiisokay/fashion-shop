import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Edit, Trash2, Search } from 'lucide-react';
import Button from '../../components/ui/Button';
import DataTable from '../../components/admin/DataTable';
import { formatPrice, isSaleActive } from '../../utils/format';

const MOCK_PRODUCTS = [
  { id: 1, name: 'Áo Thun Basic Nam', category: 'Áo', price: 250000, salePrice: 199000, saleStartDate: '2026-03-01T00:00', saleEndDate: '2026-04-30T23:59', stock: 150, status: 'ACTIVE' },
  { id: 2, name: 'Quần Jean Ống Rộng', category: 'Quần', price: 550000, stock: 85, status: 'ACTIVE' },
  { id: 3, name: 'Váy Hoa Mùa Hè', category: 'Váy', price: 450000, salePrice: 350000, saleStartDate: '2026-03-15T00:00', saleEndDate: '2026-05-15T23:59', stock: 42, status: 'ACTIVE' },
  { id: 4, name: 'Áo Khoác Denim', category: 'Áo Khoác', price: 850000, stock: 0, status: 'OUT_OF_STOCK' },
  { id: 5, name: 'Áo Sơ Mi Cổ Tàu', category: 'Áo', price: 350000, stock: 120, status: 'ACTIVE' },
];

const ProductManagePage = () => {
  const [search, setSearch] = useState('');

  const filteredProducts = MOCK_PRODUCTS.filter(p => 
    p.name.toLowerCase().includes(search.toLowerCase()) || 
    p.category.toLowerCase().includes(search.toLowerCase())
  );

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', render: (val) => <span className="font-medium text-gray-500">#{val}</span> },
    { title: 'Tên sản phẩm', dataIndex: 'name', key: 'name', render: (val) => <span className="font-medium text-gray-900">{val}</span> },
    { title: 'Danh mục', dataIndex: 'category', key: 'category' },
    { 
      title: 'Giá', 
      dataIndex: 'price', 
      key: 'price', 
      render: (val, record) => {
        const hasActiveSale = record.salePrice && isSaleActive(record.saleStartDate, record.saleEndDate);
        return (
          <div className="flex flex-col">
            {hasActiveSale ? (
              <>
                <span className="text-red-600 font-medium">{formatPrice(record.salePrice)}</span>
                <span className="text-xs text-gray-400 line-through">{formatPrice(val)}</span>
              </>
            ) : (
              <span>{formatPrice(val)}</span>
            )}
          </div>
        );
      } 
    },
    { title: 'Tồn kho', dataIndex: 'stock', key: 'stock' },
    { 
      title: 'Trạng thái', 
      dataIndex: 'status', 
      key: 'status',
      render: (val) => (
        <span className={`px-2.5 py-1 rounded-full text-xs font-medium ${
          val === 'ACTIVE' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
        }`}>
          {val === 'ACTIVE' ? 'Đang bán' : 'Hết hàng'}
        </span>
      )
    },
    { 
      title: 'Hành động', 
      dataIndex: 'id', 
      key: 'action',
      render: (id) => (
        <div className="flex items-center gap-2">
          <Link to={`/admin/products/form?id=${id}`}>
            <Button variant="ghost" size="sm" className="p-2 text-blue-600 hover:bg-blue-50">
              <Edit className="w-4 h-4" />
            </Button>
          </Link>
          <Button variant="ghost" size="sm" className="p-2 text-red-600 hover:bg-red-50" onClick={() => alert('Xóa sản phẩm ' + id)}>
            <Trash2 className="w-4 h-4" />
          </Button>
        </div>
      )
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Sản phẩm</h1>
        <Link to="/admin/products/form">
          <Button className="flex items-center gap-2">
            <Plus className="w-4 h-4" /> Thêm sản phẩm
          </Button>
        </Link>
      </div>

      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-4 border-b border-gray-200">
          <div className="relative max-w-md">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
              <Search className="h-5 w-5 text-gray-400" />
            </div>
            <input
              type="text"
              placeholder="Tìm kiếm sản phẩm..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="block w-full pl-10 pr-3 py-2 border border-gray-300 rounded-lg focus:ring-black focus:border-black sm:text-sm"
            />
          </div>
        </div>
        <DataTable 
          columns={columns} 
          data={filteredProducts} 
          emptyText="Không tìm thấy sản phẩm nào"
        />
      </div>
    </div>
  );
};

export default ProductManagePage;
