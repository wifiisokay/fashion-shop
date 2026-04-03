import { Routes, Route, Outlet } from 'react-router-dom';
import { ROUTES } from '../constants/routes';
import PrivateRoute from './PrivateRoute';
import RoleRoute from './RoleRoute';
import MainLayout from '../components/common/MainLayout';

import StaffReturnManagePage from '../pages/staff/StaffReturnManagePage';
import StaffOrderListPage from '../pages/staff/StaffOrderListPage';
import StaffOrderDetailPage from '../pages/staff/StaffOrderDetailPage';
import HomePage from '../pages/customer/HomePage';
import ProductListPage from '../pages/customer/ProductListPage';
import ProductDetailPage from '../pages/customer/ProductDetailPage';
import CartPage from '../pages/customer/CartPage';
import OrderListPage from '../pages/customer/OrderListPage';
import LoginPage from '../pages/auth/LoginPage';
import RegisterPage from '../pages/auth/RegisterPage';
import ForgotPasswordPage from '../pages/auth/ForgotPasswordPage';
import ResetPasswordPage from '../pages/auth/ResetPasswordPage';
import CheckoutPage from '../pages/customer/CheckoutPage';
import PaymentResultPage from '../pages/customer/PaymentResultPage';
import OrderDetailPage from '../pages/customer/OrderDetailPage';
import ProfilePage from '../pages/customer/ProfilePage';

import DashboardPage from '../pages/admin/DashboardPage';
import ProductManagePage from '../pages/admin/ProductManagePage';
import ProductFormPage from '../pages/admin/ProductFormPage';
import CategoryManagePage from '../pages/admin/CategoryManagePage';
import UserManagePage from '../pages/admin/UserManagePage';

const AppRouter = () => {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        {/* Public */}
        <Route path={ROUTES.HOME} element={<HomePage />} />
        <Route path={ROUTES.PRODUCTS} element={<ProductListPage />} />
        <Route path={ROUTES.PRODUCT_DETAIL} element={<ProductDetailPage />} />
        <Route path={ROUTES.LOGIN} element={<LoginPage />} />
        <Route path={ROUTES.REGISTER} element={<RegisterPage />} />
        <Route path={ROUTES.FORGOT_PASSWORD} element={<ForgotPasswordPage />} />
        <Route path={ROUTES.RESET_PASSWORD} element={<ResetPasswordPage />} />

        {/* Customer only */}
        <Route path={ROUTES.CART} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER']}><CartPage /></RoleRoute></PrivateRoute>
        } />
        <Route path={ROUTES.CHECKOUT} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER']}><CheckoutPage /></RoleRoute></PrivateRoute>
        } />
        <Route path={ROUTES.PAYMENT_RESULT} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER']}><PaymentResultPage /></RoleRoute></PrivateRoute>
        } />
        <Route path={ROUTES.MY_ORDERS} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER']}><OrderListPage /></RoleRoute></PrivateRoute>
        } />
        <Route path={ROUTES.MY_ORDER_DETAIL} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER']}><OrderDetailPage /></RoleRoute></PrivateRoute>
        } />
        <Route path={ROUTES.PROFILE} element={
          <PrivateRoute><RoleRoute allowedRoles={['CUSTOMER', 'EMPLOYEE', 'ADMIN']}><ProfilePage /></RoleRoute></PrivateRoute>
        } />

        {/* Staff — EMPLOYEE + ADMIN */}
        <Route path="/staff" element={
          <PrivateRoute><RoleRoute allowedRoles={['EMPLOYEE', 'ADMIN']}><Outlet /></RoleRoute></PrivateRoute>
        }>
          <Route path="orders" element={<StaffOrderListPage />} />
          <Route path="orders/:id" element={<StaffOrderDetailPage />} />
          <Route path="returns" element={<StaffReturnManagePage />} />
        </Route>

        {/* Admin only */}
        <Route path="/admin" element={
          <PrivateRoute><RoleRoute allowedRoles={['ADMIN']}><Outlet /></RoleRoute></PrivateRoute>
        }>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="products" element={<ProductManagePage />} />
          <Route path="products/form" element={<ProductFormPage />} />
          <Route path="categories" element={<CategoryManagePage />} />
          <Route path="users" element={<UserManagePage />} />
        </Route>
      </Route>
    </Routes>
  );
};

export default AppRouter;
