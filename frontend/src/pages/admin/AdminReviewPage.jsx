import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { reviewApi } from '../../api/reviewApi';
import DataTable from '../../components/admin/DataTable';
import StarRating from '../../components/review/StarRating';
import { formatDate } from '../../utils/format';
import { MessageSquare, Star, ArrowRight, FilterX } from 'lucide-react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../../components/ui/tabs';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useCategories } from '@/hooks/useCategories';

const AdminReviewPage = () => {
  const [activeTab, setActiveTab] = useState('stats');
  
  // Stats Tab State
  const [statsPage, setStatsPage] = useState(0);
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  
  // Reviews Tab State
  const [reviewsPage, setReviewsPage] = useState(0);
  const [selectedProductId, setSelectedProductId] = useState(null);

  const { data: categories = [] } = useCategories();

  // 1. Fetch Product Stats
  const { data: statsData, isLoading: isLoadingStats } = useQuery({
    queryKey: ['admin-review-stats', statsPage, selectedCategoryId],
    queryFn: () => reviewApi.getProductReviewStats({ 
      page: statsPage, 
      size: 10,
      ...(selectedCategoryId && { categoryId: selectedCategoryId })
    }).then(r => r.data?.data),
  });

  // 2. Fetch All/Filtered Reviews
  const { data: reviewsData, isLoading: isLoadingReviews } = useQuery({
    queryKey: ['admin-reviews', selectedProductId, reviewsPage],
    queryFn: () => reviewApi.getAllReviews({ 
      page: reviewsPage, 
      size: 20, 
      ...(selectedProductId && { productId: selectedProductId }) 
    }).then(r => r.data?.data),
  });

  // Handle Action: Xem chi tiết bình luận
  const handleViewDetails = (productId) => {
    setSelectedProductId(productId);
    setReviewsPage(0);
    setActiveTab('reviews');
  };

  const clearFilter = () => {
    setSelectedProductId(null);
    setReviewsPage(0);
  };

  // ================= Columns for Stats =================
  const statsColumns = [
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, r) => (
        <div className="flex items-center gap-3">
          {r.productImage && (
            <img src={r.productImage} alt={r.productName} className="w-12 h-12 rounded-lg object-cover border border-gray-100" referrerPolicy="no-referrer" />
          )}
          <span className="font-medium text-gray-900 line-clamp-2 max-w-[250px]">{r.productName || '—'}</span>
        </div>
      )
    },
    {
      title: 'Đánh giá Trung bình', dataIndex: 'avgRating', key: 'avgRating',
      render: (val) => (
        <div className="flex items-center gap-2">
          <Star className={`w-5 h-5 ${val >= 4 ? 'text-amber-400 fill-amber-400' : val >= 3 ? 'text-yellow-400 fill-yellow-400' : 'text-red-400 fill-red-400'}`} />
          <span className="font-bold text-gray-900">{val ? val.toFixed(1) : '0.0'}</span>
        </div>
      )
    },
    {
      title: 'Tổng số Bình luận', dataIndex: 'totalReviews', key: 'totalReviews',
      render: (val) => <span className="font-medium text-gray-600">{val} đánh giá</span>
    },
    {
      title: 'Thao tác', key: 'action',
      render: (_, r) => (
        <button 
          onClick={() => handleViewDetails(r.productId)}
          className="flex items-center text-indigo-600 hover:text-indigo-800 font-medium text-sm transition-colors"
        >
          Xem chi tiết <ArrowRight className="w-4 h-4 ml-1" />
        </button>
      )
    }
  ];

  // ================= Columns for Reviews =================
  const reviewColumns = [
    {
      title: 'Khách hàng', dataIndex: 'customerName', key: 'customerName',
      render: (val) => <span className="font-medium text-gray-900">{val}</span>
    },
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, r) => (
        <div className="flex items-center gap-2">
          {r.productImage && (
            <img src={r.productImage} alt={r.productName} className="w-8 h-8 rounded object-cover" referrerPolicy="no-referrer" />
          )}
          <span className="text-sm text-gray-600 line-clamp-1 max-w-[200px]">{r.productName || '—'}</span>
        </div>
      )
    },
    {
      title: 'Mức độ', dataIndex: 'rating', key: 'rating',
      render: (val) => <StarRating value={val} size={14} />
    },
    {
      title: 'Nội dung', dataIndex: 'comment', key: 'comment',
      render: (val) => (
        <p className="text-sm text-gray-700 max-w-sm line-clamp-2" title={val}>
          {val || <span className="italic text-gray-400">Không có bình luận</span>}
        </p>
      )
    },
    {
      title: 'Ngày gửi', dataIndex: 'createdAt', key: 'createdAt',
      render: (val) => <span className="text-sm text-gray-500">{formatDate(val)}</span>
    },
  ];

  // Flatten categories for select
  const categoryOptions = [];
  categories.forEach((root) => {
    categoryOptions.push({ value: root.id.toString(), label: root.name });
    (root.children || []).forEach((child) => {
      categoryOptions.push({ value: child.id.toString(), label: `  └ ${child.name}` });
    });
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <MessageSquare className="w-7 h-7 text-indigo-600" />
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Đánh giá</h1>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
        <TabsList className="mb-6 grid w-full max-w-md grid-cols-2">
          <TabsTrigger value="stats">Thống kê Sản phẩm</TabsTrigger>
          <TabsTrigger value="reviews">Danh sách Đánh giá</TabsTrigger>
        </TabsList>

        {/* TAB 1: PRODUCT STATS */}
        <TabsContent value="stats">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            {/* Thanh công cụ / Filter */}
            <div className="p-4 border-b border-gray-200 bg-gray-50 flex items-center justify-between">
              <h3 className="font-medium text-gray-800">Thống kê theo Sản phẩm</h3>
              <Select
                value={selectedCategoryId || '_all'}
                onValueChange={(val) => {
                  setSelectedCategoryId(val === '_all' ? '' : val);
                  setStatsPage(0);
                }}
              >
                <SelectTrigger className="w-[200px] bg-white"><SelectValue placeholder="Tất cả danh mục" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="_all">Tất cả danh mục</SelectItem>
                  {categoryOptions.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <DataTable
              columns={statsColumns}
              data={statsData?.content || []}
              emptyText="Chưa có dữ liệu thống kê"
              loading={isLoadingStats}
            />

            {/* Pagination cho Tab 1 */}
            {statsData && statsData.totalPages > 1 && (
              <div className="flex justify-center gap-2 py-4 border-t border-gray-200">
                <button disabled={statsPage === 0} onClick={() => setStatsPage(p => p - 1)}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 text-sm font-medium">
                  Trước
                </button>
                <span className="py-2 px-4 text-sm font-medium text-gray-700">
                  Trang {statsPage + 1} / {statsData.totalPages}
                </span>
                <button disabled={statsPage >= statsData.totalPages - 1} onClick={() => setStatsPage(p => p + 1)}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 text-sm font-medium">
                  Tiếp
                </button>
              </div>
            )}
          </div>
        </TabsContent>

        {/* TAB 2: DETAILED REVIEWS */}
        <TabsContent value="reviews">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            {/* Thanh công cụ / Filter */}
            <div className="p-4 border-b border-gray-200 flex justify-between items-center bg-gray-50">
              <h3 className="font-medium text-gray-800">
                {selectedProductId ? 'Đang lọc bình luận theo Sản phẩm' : 'Tất cả bình luận'}
              </h3>
              {selectedProductId && (
                <button 
                  onClick={clearFilter}
                  className="flex items-center text-sm text-red-600 hover:text-red-700 font-medium bg-red-50 px-3 py-1.5 rounded-md transition-colors"
                >
                  <FilterX className="w-4 h-4 mr-1.5" /> Xóa bộ lọc
                </button>
              )}
            </div>

            <DataTable
              columns={reviewColumns}
              data={reviewsData?.content || []}
              emptyText="Không có đánh giá nào phù hợp"
              loading={isLoadingReviews}
            />

            {/* Pagination cho Tab 2 */}
            {reviewsData && reviewsData.totalPages > 1 && (
              <div className="flex justify-center gap-2 py-4 border-t border-gray-200">
                <button disabled={reviewsPage === 0} onClick={() => setReviewsPage(p => p - 1)}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 text-sm font-medium">
                  Trước
                </button>
                <span className="py-2 px-4 text-sm font-medium text-gray-700">
                  Trang {reviewsPage + 1} / {reviewsData.totalPages}
                </span>
                <button disabled={reviewsPage >= reviewsData.totalPages - 1} onClick={() => setReviewsPage(p => p + 1)}
                  className="px-4 py-2 border rounded-lg hover:bg-gray-50 disabled:opacity-50 text-sm font-medium">
                  Tiếp
                </button>
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
};

export default AdminReviewPage;
