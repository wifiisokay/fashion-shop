import { Navigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const RoleRoute = ({ children, allowedRoles }) => {
  const { user, isLoading } = useAuth();

  if (isLoading) return <div>Loading...</div>;

  if (!allowedRoles.includes(user?.role)) return <Navigate to="/" replace />;
  return children;
};

export default RoleRoute;
