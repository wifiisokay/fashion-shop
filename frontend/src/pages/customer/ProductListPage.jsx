import { useState, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useProducts } from '../../hooks/useProducts';
import { useCategories } from '../../hooks/useCategories';
import ProductCard from '../../components/product/ProductCard';
import Spinner from '../../components/ui/Spinner';
import { Filter, X } from 'lucide-react';
import { clsx } from 'clsx';

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'UNISEX', label: 'Unisex' }
];

const ProductListPage = () => {
  const [searchParams] = useSearchParams();
  
  const [filters, setFilters] = useState({
    categoryId: searchParams.get('category') ? parseInt(searchParams.get('category')) : '',
    gender: searchParams.get('gender') || '',
    isSale: searchParams.get('isSale') === 'true',
  });
  
  const [sortBy, setSortBy] = useState('newest');
  const [page, setPage] = useState(0);
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);

  // Fetch true category tree
  const { data: categoriesData = [] } = useCategories();
  
  const categoryOptions = useMemo(() => {
    let opts = [];
    categoriesData.forEach(root => {
      opts.push({ id: root.id, name: root.name, isRoot: true });
      (root.children || []).forEach(child => {
        opts.push({ id: child.id, name: child.name, isRoot: false });
      });
    });
    return opts;
  }, [categoriesData]);

  // Request to true backend API
  const queryParams = {
    categoryId: filters.categoryId || undefined,
    gender: filters.gender || undefined,
    isSale: filters.isSale || undefined,
    sort: sortBy,
    page,
    size: 12
  };
  
  const { data, isLoading, isError } = useProducts(queryParams);

  const products = data?.content || [];
  const totalPages = data?.totalPages || 0;

  const handleFilterChange = (key, value) => {
    setFilters(prev => ({
      ...prev,
      [key]: prev[key] === value ? (typeof value === 'boolean' ? false : '') : value
    }));
    setPage(0);
  };

  const clearFilters = () => {
    setFilters({ categoryId: '', gender: '', isSale: false });
    setPage(0);
  };

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
            onChange={(e) => {
              setSortBy(e.target.value);
              setPage(0);
            }}
            className="border-gray-300 rounded-lg text-sm focus:ring-black focus:border-black p-2.5 border bg-white"
          >
            <option value="newest">Mới nhất</option>
            <option value="price_asc">Giá: Thấp đến Cao</option>
            <option value="price_desc">Giá: Cao đến Thấp</option>
            <option value="name_asc">Tên (A-Z)</option>
          </select>
        </div>
      </div>

      <div className="flex flex-col lg:flex-row gap-8">
        {/* Sidebar Filters (Desktop) */}
        <div className="hidden lg:block w-64 flex-shrink-0 space-y-8">
          <div>
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-bold text-gray-900">Bộ lọc</h3>
              {(filters.categoryId || filters.gender || filters.isSale) && (
                <button onClick={clearFilters} className="text-xs text-blue-600 hover:underline">Xóa tất cả</button>
              )}
            </div>
          </div>

          {/* Category Filter */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Danh mục</h4>
            <div className="space-y-2 max-h-64 overflow-y-auto pr-2">
              {categoryOptions.map(cat => (
                <label key={cat.id} className={`flex items-center gap-2 cursor-pointer ${!cat.isRoot ? 'ml-4' : ''}`}>
                  <input
                    type="checkbox"
                    checked={filters.categoryId === cat.id}
                    onChange={() => handleFilterChange('categoryId', cat.id)}
                    className="rounded border-gray-300 text-black focus:ring-black"
                  />
                  <span className={`text-sm ${cat.isRoot ? 'font-medium text-gray-800' : 'text-gray-600'}`}>{cat.name}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Gender Filter */}
          <div>
            <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Giới tính</h4>
            <div className="flex flex-wrap gap-2">
              {GENDER_OPTIONS.map(opt => (
                <button
                  key={opt.value}
                  onClick={() => handleFilterChange('gender', opt.value)}
                  className={clsx(
                    "px-3 py-1.5 text-xs rounded-full border transition-colors",
                    filters.gender === opt.value
                      ? "bg-black text-white border-black"
                      : "bg-white text-gray-600 border-gray-300 hover:border-gray-400"
                  )}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Sale Filter */}
          <div>
            <label className="flex items-center gap-2 cursor-pointer mt-4 border p-3 rounded-lg hover:border-red-500 transition-colors">
              <input
                type="checkbox"
                checked={filters.isSale}
                onChange={() => handleFilterChange('isSale', !filters.isSale)}
                className="rounded border-gray-300 text-red-500 focus:ring-red-500 w-5 h-5"
              />
              <span className="text-sm font-semibold text-red-600">Đang khuyến mãi</span>
            </label>
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
                    {categoryOptions.map(cat => (
                      <label key={cat.id} className={`flex items-center gap-3 cursor-pointer ${!cat.isRoot ? 'ml-4' : ''}`}>
                        <input
                          type="checkbox"
                          checked={filters.categoryId === cat.id}
                          onChange={() => handleFilterChange('categoryId', cat.id)}
                          className="w-5 h-5 rounded border-gray-300 text-black focus:ring-black"
                        />
                        <span className={`text-sm ${cat.isRoot ? 'font-medium text-gray-800' : 'text-gray-600'}`}>{cat.name}</span>
                      </label>
                    ))}
                  </div>
                </div>
                {/* Mobile Gender */}
                <div>
                  <h4 className="font-semibold text-sm text-gray-900 mb-3 uppercase tracking-wider">Giới tính</h4>
                  <div className="flex flex-wrap gap-2">
                    {GENDER_OPTIONS.map(opt => (
                      <button
                        key={opt.value}
                        onClick={() => handleFilterChange('gender', opt.value)}
                        className={clsx(
                          "px-4 py-2 text-sm rounded-full border transition-colors",
                          filters.gender === opt.value
                            ? "bg-black text-white border-black"
                            : "bg-white text-gray-600 border-gray-300"
                        )}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>
                {/* Mobile Sale */}
                <div>
                  <label className="flex items-center gap-3 cursor-pointer border p-3 rounded-lg">
                    <input
                      type="checkbox"
                      checked={filters.isSale}
                      onChange={() => handleFilterChange('isSale', !filters.isSale)}
                      className="w-5 h-5 rounded border-gray-300 text-red-500 focus:ring-red-500"
                    />
                    <span className="text-sm font-semibold text-red-600">Đang khuyến mãi</span>
                  </label>
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
        <div className="flex-1 flex flex-col">
          {isLoading ? (
            <div className="flex justify-center py-20"><Spinner size="lg" /></div>
          ) : isError ? (
            <div className="text-center py-20 text-red-500">Lỗi tải dữ liệu sản phẩm</div>
          ) : products.length === 0 ? (
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
            <>
              <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4 sm:gap-6">
                {products.map(product => (
                  <ProductCard
                    key={product.id}
                    product={product}
                  />
                ))}
              </div>
              
              {/* Pagination UI */}
              {totalPages > 1 && (
                <div className="flex justify-center gap-2 mt-10">
                  <button
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                    className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50"
                  >
                    Trước
                  </button>
                  <span className="py-2 px-4 font-medium">Trang {page + 1} / {totalPages}</span>
                  <button
                    disabled={page >= totalPages - 1}
                    onClick={() => setPage(page + 1)}
                    className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50"
                  >
                    Tiếp
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProductListPage;
