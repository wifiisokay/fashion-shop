import { Link } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';

const HomePage = () => {
  return (
    <div className="space-y-16 pb-16">
      {/* Hero Section */}
      <section className="relative h-[60vh] min-h-[500px] flex items-center justify-center rounded-3xl overflow-hidden">
        <img 
          src="https://picsum.photos/seed/fashion-hero/1920/1080" 
          alt="Fashion Hero" 
          className="absolute inset-0 w-full h-full object-cover"
          referrerPolicy="no-referrer"
        />
        <div className="absolute inset-0 bg-black/40" />
        <div className="relative z-10 text-center text-white space-y-6 max-w-2xl px-4">
          <h1 className="text-5xl md:text-6xl font-bold tracking-tight">Mùa Hè Rực Rỡ</h1>
          <p className="text-lg md:text-xl text-gray-200">
            Khám phá bộ sưu tập mới nhất với những thiết kế độc quyền, mang lại phong cách tự tin cho bạn.
          </p>
          <Link to={ROUTES.PRODUCTS} className="inline-block mt-4">
            <Button size="lg" className="bg-white text-black hover:bg-gray-100 font-semibold px-8">
              Mua Sắm Ngay
            </Button>
          </Link>
        </div>
      </section>

      {/* Categories / Banners */}
      <section className="grid md:grid-cols-2 gap-6">
        <Link to={ROUTES.PRODUCTS} className="group relative h-80 rounded-2xl overflow-hidden block">
          <img 
            src="https://picsum.photos/seed/mens-fashion/800/600" 
            alt="Thời trang nam" 
            className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            referrerPolicy="no-referrer"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
          <div className="absolute bottom-6 left-6 text-white">
            <h3 className="text-2xl font-bold mb-2">Thời Trang Nam</h3>
            <span className="text-sm font-medium uppercase tracking-wider">Xem bộ sưu tập &rarr;</span>
          </div>
        </Link>
        <Link to={ROUTES.PRODUCTS} className="group relative h-80 rounded-2xl overflow-hidden block">
          <img 
            src="https://picsum.photos/seed/womens-fashion/800/600" 
            alt="Thời trang nữ" 
            className="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
            referrerPolicy="no-referrer"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />
          <div className="absolute bottom-6 left-6 text-white">
            <h3 className="text-2xl font-bold mb-2">Thời Trang Nữ</h3>
            <span className="text-sm font-medium uppercase tracking-wider">Xem bộ sưu tập &rarr;</span>
          </div>
        </Link>
      </section>
    </div>
  );
};

export default HomePage;
