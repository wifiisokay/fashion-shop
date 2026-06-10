import { Outlet, useLocation } from 'react-router-dom';
import Navbar from '../common/Navbar';
import ChatWidget from '../chat/ChatWidget';
import { useAuth } from '../../contexts/AuthContext';

const MainLayout = () => {
  const { pathname } = useLocation();
  const { user } = useAuth();
  const isBackOfficeRoute = pathname.startsWith('/admin') || pathname.startsWith('/staff');
  const shouldShowChat = user?.role === 'CUSTOMER' && !isBackOfficeRoute;

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 relative">
      <Navbar />
      <main className="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <Outlet />
      </main>
      <footer className="bg-white border-t border-gray-200 py-8 mt-auto">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center text-sm text-gray-500">
          &copy; {new Date().getFullYear()} Fashion Shop. All rights reserved.
        </div>
      </footer>
      {shouldShowChat && <ChatWidget />}
    </div>
  );
};

export default MainLayout;
