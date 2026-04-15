import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { ROUTES } from '../../constants/routes';
import { LogOut, ShoppingCart, User } from 'lucide-react';

const Navbar = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate(ROUTES.LOGIN);
  };

  const customerMenuItems = [
    { label: 'Sản phẩm', path: ROUTES.PRODUCTS },
    { label: 'Đơn hàng của tôi', path: ROUTES.MY_ORDERS },
  ];

  const staffMenuItems = [
    { label: 'Đơn hàng', path: ROUTES.STAFF_ORDERS },
    { label: 'Đổi/Trả hàng', path: ROUTES.STAFF_RETURNS },
  ];

  const adminMenuItems = [
    { label: 'Dashboard', path: ROUTES.ADMIN_DASHBOARD },
    { label: 'Sản phẩm', path: ROUTES.ADMIN_PRODUCTS },
    { label: 'Người dùng', path: ROUTES.ADMIN_USERS },
    { label: 'Đơn hàng', path: ROUTES.STAFF_ORDERS },
    { label: 'Đổi/Trả', path: ROUTES.STAFF_RETURNS },
  ];

  const menu =
    user?.role === 'ADMIN' ? adminMenuItems :
      user?.role === 'EMPLOYEE' ? staffMenuItems :
        customerMenuItems;

  return (
    <nav className="bg-white border-b border-gray-200 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center">
            <Link to={ROUTES.HOME} className="flex-shrink-0 flex items-center">
              <span className="text-xl font-bold tracking-tight">FASHION<span className="text-gray-500">SHOP</span></span>
            </Link>
            <div className="hidden sm:ml-8 sm:flex sm:space-x-8">
              {menu.map((item) => (
                <Link
                  key={item.path}
                  to={item.path}
                  className="inline-flex items-center px-1 pt-1 border-b-2 border-transparent text-sm font-medium text-gray-500 hover:text-gray-900 hover:border-gray-300 transition-colors"
                >
                  {item.label}
                </Link>
              ))}
            </div>
          </div>
          <div className="flex items-center space-x-4">
            {isAuthenticated ? (
              <>
                {user?.role === 'CUSTOMER' && (
                  <Link to={ROUTES.CART} className="text-gray-500 hover:text-gray-900 relative">
                    <ShoppingCart className="w-6 h-6" />
                    <span className="absolute -top-1 -right-1 bg-black text-white text-[10px] font-bold w-4 h-4 rounded-full flex items-center justify-center">
                      0
                    </span>
                  </Link>
                )}
                <div className="flex items-center gap-3 ml-4 border-l pl-4">
                  <Link to={ROUTES.PROFILE} className="flex items-center gap-2 text-sm font-medium text-gray-700 hover:text-black hidden sm:flex">
                    <User className="w-4 h-4" />
                    {user?.name}
                  </Link>
                  <button
                    onClick={handleLogout}
                    className="text-gray-500 hover:text-red-600 transition-colors"
                    title="Đăng xuất"
                  >
                    <LogOut className="w-5 h-5" />
                  </button>
                </div>
              </>
            ) : (
              <div className="space-x-4">
                <Link to={ROUTES.LOGIN} className="text-sm font-medium text-gray-500 hover:text-gray-900">
                  Đăng nhập
                </Link>
                <Link to={ROUTES.REGISTER} className="text-sm font-medium bg-black text-white px-4 py-2 rounded-lg hover:bg-gray-800 transition-colors">
                  Đăng ký
                </Link>
              </div>
            )}
          </div>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
