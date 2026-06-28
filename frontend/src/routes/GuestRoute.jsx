import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

/**
 * GuestRoute: Chỉ dành cho khách chưa đăng nhập.
 * Nếu đã đăng nhập rồi (isAuthenticated === true), tự động chuyển hướng về Trang chủ '/'.
 */
const GuestRoute = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) return <div>Loading...</div>;

  if (isAuthenticated) return <Navigate to="/" replace />;
  return children;
};

export default GuestRoute;
