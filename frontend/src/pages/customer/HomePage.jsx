import { Link, Navigate } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import { useAuth } from '../../contexts/AuthContext';
import { useProducts } from '../../hooks/useProducts';
import ProductCard from '../../components/product/ProductCard';
import { Sparkles, Bot, ShoppingBag, ArrowRight, Star, Heart, ShieldCheck, Flame } from 'lucide-react';

const HomePage = () => {
  const { user } = useAuth();

  // Redirect admin and staff to their respective dashboards
  if (user?.role === 'ADMIN') {
    return <Navigate to={ROUTES.ADMIN_DASHBOARD} replace />;
  }

  if (user?.role === 'EMPLOYEE') {
    return <Navigate to={ROUTES.STAFF_ORDERS} replace />;
  }

  // Fetch 8 newest products
  const { data: productsData, isLoading: isProductsLoading, isError: isProductsError } = useProducts({
    size: 8,
    sort: 'newest'
  });

  const products = productsData?.content || [];

  // Custom event trigger to open ChatWidget
  const handleOpenAIChat = () => {
    window.dispatchEvent(new CustomEvent('open-ai-chat'));
  };

  // Skeleton Card for Loading State
  const SkeletonProductCard = () => (
    <div className="flex flex-col bg-white rounded-2xl overflow-hidden border border-gray-100 shadow-sm animate-pulse">
      <div className="aspect-[3/4] bg-gray-100" />
      <div className="p-4 space-y-2.5">
        <div className="h-4 bg-gray-200 rounded w-5/6" />
        <div className="h-3 bg-gray-200 rounded w-1/2" />
        <div className="h-5 bg-gray-200 rounded w-1/3 pt-1" />
      </div>
    </div>
  );

  return (
    <div className="space-y-16 pb-16">
      
      {/* 1. Hero Section */}
      <section className="relative h-[65vh] min-h-[500px] flex items-center rounded-3xl overflow-hidden shadow-lg group">
        <img 
          src="https://picsum.photos/seed/fashion-hero/1920/1080" 
          alt="Fashion Hero" 
          className="absolute inset-0 w-full h-full object-cover transition-transform duration-1000 group-hover:scale-102"
          referrerPolicy="no-referrer"
        />
        {/* Modern dark gradient overlay */}
        <div className="absolute inset-0 bg-gradient-to-r from-black/70 via-black/40 to-transparent" />
        
        <div className="relative z-10 text-left text-white space-y-6 max-w-2xl pl-8 md:pl-16 pr-6">
          <div className="inline-flex items-center gap-1.5 bg-white/10 backdrop-blur-md px-3 py-1 rounded-full text-xs font-bold text-indigo-300 border border-white/10 uppercase tracking-widest animate-bounce">
            <Flame className="w-3.5 h-3.5 text-amber-400" />
            <span>Mùa Hè Rực Rỡ 2026</span>
          </div>
          <h1 className="text-4xl md:text-6xl font-black tracking-tight leading-tight">
            Khơi Nguồn<br />
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-400 via-purple-300 to-pink-300">Phong Cách</span> Riêng
          </h1>
          <p className="text-sm md:text-base text-gray-350 leading-relaxed max-w-lg">
            Khám phá bộ sưu tập hè độc quyền với các chất liệu tự nhiên, phom dáng tôn dáng tự tin giúp bạn tỏa sáng mọi góc phố.
          </p>
          <div className="flex flex-wrap gap-4 pt-2">
            <Link to={ROUTES.PRODUCTS} className="inline-block">
              <Button size="lg" className="bg-white text-black hover:bg-gray-100 font-bold px-8 shadow-md">
                Mua Sắm Ngay
              </Button>
            </Link>

          </div>
        </div>
      </section>

      {/* 2. Shop by Category / Gender */}
      <section className="space-y-6">
        <div className="text-center space-y-1">
          <h2 className="text-2xl md:text-3xl font-black text-gray-900 tracking-tight">Danh Mục Mua Sắm</h2>
          <p className="text-xs text-gray-400 font-medium">Bắt đầu phong cách của bạn với danh mục phù hợp</p>
        </div>
        <div className="grid md:grid-cols-2 gap-8">
          {/* Nam Category */}
          <Link 
            to={`${ROUTES.PRODUCTS}?gender=MALE`} 
            className="group relative h-96 rounded-2xl overflow-hidden block shadow-sm hover:shadow-lg transition-all duration-500"
          >
            <img 
              src="https://picsum.photos/seed/mens-fashion/900/700" 
              alt="Thời trang nam" 
              className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
              referrerPolicy="no-referrer"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/25 to-transparent transition-opacity duration-300" />
            <div className="absolute bottom-8 left-8 text-white space-y-1">
              <span className="text-[10px] font-bold text-indigo-300 uppercase tracking-widest block">Collection</span>
              <h3 className="text-3xl font-black tracking-tight">Thời Trang Nam</h3>
              <p className="text-xs text-gray-300 max-w-xs font-light">Phong cách tối giản, thanh lịch, nam tính và năng động.</p>
              <span className="inline-flex items-center gap-1 text-xs font-bold uppercase tracking-wider text-indigo-300 pt-2 group-hover:text-white transition-colors">
                <span>Khám phá ngay</span>
                <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
              </span>
            </div>
          </Link>

          {/* Nữ Category */}
          <Link 
            to={`${ROUTES.PRODUCTS}?gender=FEMALE`} 
            className="group relative h-96 rounded-2xl overflow-hidden block shadow-sm hover:shadow-lg transition-all duration-500"
          >
            <img 
              src="https://picsum.photos/seed/womens-fashion/900/700" 
              alt="Thời trang nữ" 
              className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-700"
              referrerPolicy="no-referrer"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/25 to-transparent transition-opacity duration-300" />
            <div className="absolute bottom-8 left-8 text-white space-y-1">
              <span className="text-[10px] font-bold text-indigo-300 uppercase tracking-widest block">Collection</span>
              <h3 className="text-3xl font-black tracking-tight">Thời Trang Nữ</h3>
              <p className="text-xs text-gray-300 max-w-xs font-light">Những bộ cánh kiêu sa, quyến rũ, hiện đại tôn vinh nét quyến rũ.</p>
              <span className="inline-flex items-center gap-1 text-xs font-bold uppercase tracking-wider text-indigo-300 pt-2 group-hover:text-white transition-colors">
                <span>Khám phá ngay</span>
                <ArrowRight className="w-3.5 h-3.5 group-hover:translate-x-1 transition-transform" />
              </span>
            </div>
          </Link>
        </div>
      </section>

      {/* 3. Featured Products / Latest Arrivals Section */}
      <section className="space-y-8">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-end gap-3 border-b border-gray-100 pb-4">
          <div>
            <h2 className="text-2xl md:text-3xl font-black text-gray-900 tracking-tight flex items-center gap-2">
              <ShoppingBag className="w-6 h-6 text-gray-800" />
              <span>Sản Phẩm Mới Về</span>
            </h2>
            <p className="text-xs text-gray-400 font-medium mt-0.5">Khám phá các thiết kế thời trang vừa cập bến trong tuần này</p>
          </div>
          <Link 
            to={ROUTES.PRODUCTS} 
            className="text-xs font-bold text-indigo-600 hover:text-indigo-800 transition-colors flex items-center gap-1 self-end sm:self-auto"
          >
            <span>Tất cả sản phẩm</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </Link>
        </div>

        {/* Product Grid / Skeletons */}
        {isProductsLoading ? (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {Array.from({ length: 8 }).map((_, i) => (
              <SkeletonProductCard key={i} />
            ))}
          </div>
        ) : isProductsError ? (
          <div className="text-center py-12 bg-rose-50/30 border border-rose-100 rounded-2xl text-rose-600">
            Có lỗi xảy ra khi lấy danh sách sản phẩm. Vui lòng tải lại trang.
          </div>
        ) : products.length === 0 ? (
          <div className="text-center py-12 bg-gray-50 border border-dashed rounded-2xl text-gray-400 font-medium">
            Chưa có sản phẩm nào được bày bán trong thời gian này.
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {products.map(product => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </section>

      {/* 4. AI Fashion Assistant CTA Banner */}
      <section className="bg-gradient-to-br from-indigo-950 via-indigo-900 to-slate-900 text-white rounded-3xl p-8 md:p-12 relative overflow-hidden shadow-lg flex flex-col md:flex-row items-center justify-between gap-8 group">
        
        {/* Glow circles decoration */}
        <div className="absolute top-0 right-0 w-80 h-80 bg-indigo-500/10 rounded-full blur-3xl -mr-16 -mt-16 pointer-events-none" />
        <div className="absolute bottom-0 left-0 w-64 h-64 bg-purple-500/10 rounded-full blur-3xl -ml-16 -mb-16 pointer-events-none" />

        <div className="space-y-4 max-w-xl z-10 text-left">
          <div className="inline-flex items-center gap-1.5 bg-indigo-500/20 px-3 py-1 rounded-full text-[10px] font-bold text-indigo-300 border border-indigo-500/30 uppercase tracking-widest">
            <Sparkles className="w-3.5 h-3.5" />
            <span>AI Stylist - Gemini Assistant</span>
          </div>
          <h2 className="text-2xl md:text-4xl font-black tracking-tight leading-tight">
            Trợ Lý Phối Đồ<br />Thông Minh Gemini AI
          </h2>
          <p className="text-xs md:text-sm text-indigo-200 font-light leading-relaxed">
            Bạn phân vân không biết kết hợp mẫu áo thun hay quần dài này như thế nào cho thanh lịch hay cá tính? Nhấn nút chat bên dưới để mở ngay trợ lý ảo tư vấn thời trang cá nhân của chúng tôi và tạo ra các bộ trang phục (outfit combos) độc đáo trong 1 giây!
          </p>
          <div className="flex flex-wrap gap-4 text-xs font-bold text-indigo-300/80 pt-1.5">
            <span className="flex items-center gap-1 border border-indigo-700/50 bg-indigo-950/40 px-3 py-1 rounded-lg">✨ Tự động gợi ý outfit</span>
            <span className="flex items-center gap-1 border border-indigo-700/50 bg-indigo-950/40 px-3 py-1 rounded-lg">💬 Chat hỏi đáp thông minh</span>
          </div>
        </div>

        <div className="z-10 flex-shrink-0 self-center">
          <button 
            onClick={handleOpenAIChat}
            className="inline-flex items-center gap-2.5 bg-white text-indigo-950 font-black text-sm px-8 py-4 rounded-2xl hover:bg-indigo-50 hover:scale-102 transition-all duration-300 shadow-lg shadow-black/10 group-hover:shadow-white/5"
          >
            <Bot className="w-5 h-5 text-indigo-600" />
            <span>Trải nghiệm Trợ lý AI</span>
            <ArrowRight className="w-4 h-4 ml-1 group-hover:translate-x-1 transition-transform" />
          </button>
        </div>
      </section>

      {/* 5. Trust Badges Section */}
      <section className="grid grid-cols-1 sm:grid-cols-3 gap-6 bg-gray-50/50 border border-gray-150 p-8 rounded-2xl text-center">
        <div className="space-y-2 flex flex-col items-center">
          <div className="w-12 h-12 rounded-full bg-white flex items-center justify-center border border-gray-100 shadow-sm text-indigo-600">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <h4 className="font-bold text-sm text-gray-900">Cam Kết Chính Hãng</h4>
          <p className="text-xs text-gray-400 max-w-[200px]">Tất cả sản phẩm thời trang cao cấp 100% chính hãng, rõ nguồn gốc xuất xứ.</p>
        </div>
        <div className="space-y-2 flex flex-col items-center border-t sm:border-t-0 sm:border-x border-gray-200 py-6 sm:py-0">
          <div className="w-12 h-12 rounded-full bg-white flex items-center justify-center border border-gray-100 shadow-sm text-indigo-600">
            <Bot className="w-6 h-6" />
          </div>
          <h4 className="font-bold text-sm text-gray-900">Công Nghệ AI Tiên Phong</h4>
          <p className="text-xs text-gray-400 max-w-[200px]">Trải nghiệm tư vấn mua sắm, đề xuất outfit cá nhân hóa bằng mô hình Gemini AI.</p>
        </div>
        <div className="space-y-2 flex flex-col items-center">
          <div className="w-12 h-12 rounded-full bg-white flex items-center justify-center border border-gray-100 shadow-sm text-indigo-600">
            <Heart className="w-6 h-6" />
          </div>
          <h4 className="font-bold text-sm text-gray-900">Chăm Sóc Tận Tâm</h4>
          <p className="text-xs text-gray-400 max-w-[200px]">Bộ phận hỗ trợ chăm sóc khách hàng trực tuyến 24/7 sẵn sàng giải đáp.</p>
        </div>
      </section>

    </div>
  );
};

export default HomePage;
