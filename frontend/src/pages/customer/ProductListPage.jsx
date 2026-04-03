import { useState, useMemo } from 'react';
import { useProducts } from '../../hooks/useProducts';
import ProductCard from '../../components/product/ProductCard';
import Spinner from '../../components/ui/Spinner';
import { Filter, X } from 'lucide-react';
import { clsx } from 'clsx';

const CATEGORIES = ['Áo', 'Quần', 'Váy', 'Áo Khoác'];
const COLORS = ['Đen', 'Trắng', 'Xanh', 'Đỏ', 'Be'];
const SIZES = ['S', 'M', 'L', 'XL'];

const MOCK_PRODUCTS = [
  { id: 1, name: 'Áo Thun Basic Nam', price: 250000, salePrice: 199000, saleStartDate: '2026-03-01T00:00', saleEndDate: '2026-04-30T23:59', category: 'Áo', color: 'Trắng', sizes: ['S', 'M', 'L'], isNew: true },
  { id: 2, name: 'Quần Jean Ống Rộng', price: 550000, category: 'Quần', color: 'Xanh', sizes: ['M', 'L', 'XL'], isNew: false },
  { id: 3, name: 'Váy Hoa Mùa Hè', price: 450000, salePrice: 350000, saleStartDate: '2026-03-15T00:00', saleEndDate: '2026-05-15T23:59', category: 'Váy', color: 'Đỏ', sizes: ['S', 'M'], isNew: true },
  { id: 4, name: 'Áo Khoác Denim', price: 850000, category: 'Áo Khoác', color: 'Xanh', sizes: ['L', 'XL'], isNew: false },
  { id: 5, name: 'Áo Sơ Mi Cổ Tàu', price: 350000, category: 'Áo', color: 'Đen', sizes: ['M', 'L'], isNew: true },
  { id: 6, name: 'Quần Short Kaki', price: 290000, salePrice: 250000, saleStartDate: '2026-03-01T00:00', saleEndDate: '2026-03-10T23:59', category: 'Quần', color: 'Be', sizes: ['S', 'M', 'L', 'XL'], isNew: false },
  { id: 7, name: 'Áo Len Cổ Lọ', price: 420000, category: 'Áo', color: 'Đen', sizes: ['M', 'L'], isNew: true },
  { id: 8, name: 'Chân Váy Xếp Ly', price: 320000, category: 'Váy', color: 'Trắng', sizes: ['S', 'M'], isNew: false },
];

const ProductListPage = () => {
  const [filters, setFilters] = useState({ category: '', color: '', size: '' });
  const [sortBy, setSortBy] = useState('new');
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);

  // Pass filters to API
  const { data, isLoading, isError } = useProducts({ ...filters, sort: sortBy });

  const products = data?.content?.length ? data.content : MOCK_PRODUCTS;

  // Apply local filtering & sorting for mock data
  const filteredAndSortedProducts = useMemo(() => {
    let result = [...products];

    if (filters.category) {
      result = result.filter(p => p.category === filters.category);
    }
    if (filters.color) {
      result = result.filter(p => p.color === filters.color);
    }
    if (filters.size) {
      result = result.filter(p => p.sizes?.includes(filters.size));
    }

    if (sortBy === 'price_asc') {
      result.sort((a, b) => a.price - b.price);
    } else if (sortBy === 'price_desc') {
      result.sort((a, b) => b.price - a.price);
    } else if (sortBy === 'new') {
      result.sort((a, b) => (b.isNew === a.isNew ? 0 : b.isNew ? 1 : -1));
    }

    return result;
  }, [products, filters, sortBy]);

  const handleFilterChange = (key, value) => {
    setFilters(prev => ({
      ...prev,
      [key]: prev[key] === value ? '' : value // toggle filter
    }));
  };

  const clearFilters = () => {
    setFilters({ category: '', color: '', size: '' });
  };

  if (isLoading) return <div className="flex justify-center py-20"><Spinner size="lg" /></div>;
  if (isError) return <div className="text-center py-20 text-red-500">Lỗi tải dữ liệu sản phẩm</div>;

  return (
    <div className="space-y-6">
      {/* Header & Sort */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-gray-200 pb-4">
        <h1 className="text-3xl font-bold text-gray-900">Tất cả sản phẩm</h1>
        <div className="flex items-center gap-3">
          <button 
            className="lg:hidden flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium"
            onClick={() => setIsMobileFilterOpen(true)}
          >
            <Filter className="w-4 h-4" /> Lọc
          </button>
          <select 
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2.5 border bg-white"
          >
            <option value="new">Mới nhất</option>
            <option value="price_asc">Giá: Thấp đến Cao</option>
            <option value="price_desc">Giá: Cao đến Thấp</option>
          </select>
        </div>
      </div>
      
      <div className="flex flex-col lg:flex-row gap-8">
        {/* Sidebar Filters (Desktop) */}
        <div className="hidden lg:block w-64 flex-shrink-0 space-y-8">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-gray-900">Bộ lọc</h3>
              {(filters.category || filters.color || filters.size) && (
                <button onClick={clearFilters} className="text-xs text-blue-600 hover:underline">Xóa tất cả</button>
              )}
            </div>
          </div>

          {/* Category Filter */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Danh mục</h4>
            <div className="space-y-2">
              {CATEGORIES.map(cat => (
                <label key={cat} className="flex items-center gap-2 cursor-pointer">
                  <input 
                    type="checkbox" 
                    checked={filters.category === cat}
                    onChange={() => handleFilterChange('category', cat)}
                    className="rounded border-gray-300 text-black focus:ring-black"
                  />
                  <span className="text-sm text-gray-600">{cat}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Color Filter */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Màu sắc</h4>
            <div className="flex flex-wrap gap-2">
              {COLORS.map(color => (
                <button
                  key={color}
                  onClick={() => handleFilterChange('color', color)}
                  className={clsx(
                    "px-3 py-1.5 text-xs rounded-full border transition-colors",
                    filters.color === color 
                      ? "bg-black text-white border-black" 
                      : "bg-white text-gray-600 border-gray-300 hover:border-gray-400"
                  )}
                >
                  {color}
                </button>
              ))}
            </div>
          </div>

          {/* Size Filter */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Kích thước</h4>
            <div className="grid grid-cols-4 gap-2">
              {SIZES.map(size => (
                <button
                  key={size}
                  onClick={() => handleFilterChange('size', size)}
                  className={clsx(
                    "py-2 text-xs font-medium rounded border transition-colors text-center",
                    filters.size === size 
                      ? "bg-black text-white border-black" 
                      : "bg-white text-gray-600 border-gray-300 hover:border-gray-400"
                  )}
                >
                  {size}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Mobile Filter Drawer */}
        {isMobileFilterOpen && (
          <div className="fixed inset-0 z-50 lg:hidden flex">
            <div className="fixed inset-0 bg-black/50" onClick={() => setIsMobileFilterOpen(false)} />
            <div className="relative w-4/5 max-w-xs bg-white h-full flex flex-col shadow-xl">
              <div className="flex items-center justify-between p-4 border-b border-gray-200">
                <h3 className="font-bold text-lg">Bộ lọc</h3>
                <button onClick={() => setIsMobileFilterOpen(false)} className="p-2 text-gray-500 hover:text-black">
                  <X className="w-5 h-5" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto p-4 space-y-8">
                {/* Mobile Categories */}
                <div>
                  <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Danh mục</h4>
                  <div className="space-y-3">
                    {CATEGORIES.map(cat => (
                      <label key={cat} className="flex items-center gap-3 cursor-pointer">
                        <input 
                          type="checkbox" 
                          checked={filters.category === cat}
                          onChange={() => handleFilterChange('category', cat)}
                          className="w-5 h-5 rounded border-gray-300 text-black focus:ring-black"
                        />
                        <span className="text-gray-700">{cat}</span>
                      </label>
                    ))}
                  </div>
                </div>
                {/* Mobile Colors */}
                <div>
                  <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Màu sắc</h4>
                  <div className="flex flex-wrap gap-2">
                    {COLORS.map(color => (
                      <button
                        key={color}
                        onClick={() => handleFilterChange('color', color)}
                        className={clsx(
                          "px-4 py-2 text-sm rounded-full border transition-colors",
                          filters.color === color 
                            ? "bg-black text-white border-black" 
                            : "bg-white text-gray-600 border-gray-300"
                        )}
                      >
                        {color}
                      </button>
                    ))}
                  </div>
                </div>
                {/* Mobile Sizes */}
                <div>
                  <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Kích thước</h4>
                  <div className="grid grid-cols-4 gap-2">
                    {SIZES.map(size => (
                      <button
                        key={size}
                        onClick={() => handleFilterChange('size', size)}
                        className={clsx(
                          "py-3 text-sm font-medium rounded border transition-colors text-center",
                          filters.size === size 
                            ? "bg-black text-white border-black" 
                            : "bg-white text-gray-600 border-gray-300"
                        )}
                      >
                        {size}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
              <div className="p-4 border-t border-gray-200 flex gap-3">
                <button 
                  onClick={clearFilters}
                  className="flex-1 py-3 border border-gray-300 rounded-lg font-medium text-gray-700"
                >
                  Xóa
                </button>
                <button 
                  onClick={() => setIsMobileFilterOpen(false)}
                  className="flex-1 py-3 bg-black text-white rounded-lg font-medium"
                >
                  Áp dụng
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Product Grid */}
        <div className="flex-1">
          {filteredAndSortedProducts.length === 0 ? (
            <div className="text-center py-20 bg-gray-50 rounded-2xl border border-dashed border-gray-300">
              <p className="text-gray-500 mb-4">Không tìm thấy sản phẩm nào phù hợp với bộ lọc.</p>
              <button 
                onClick={clearFilters}
                className="px-6 py-2 bg-black text-white rounded-lg text-sm font-medium hover:bg-gray-800 transition-colors"
              >
                Xóa bộ lọc
              </button>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-4 sm:gap-6">
              {filteredAndSortedProducts.map(product => (
                <ProductCard 
                  key={product.id} 
                  product={product} 
                  onAddToCart={(p) => console.log('Add to cart', p)} 
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProductListPage;
