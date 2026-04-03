import { Link, useSearchParams } from 'react-router-dom';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import { CheckCircle2, XCircle } from 'lucide-react';

const PaymentResultPage = () => {
  const [searchParams] = useSearchParams();
  const status = searchParams.get('status');
  const orderId = searchParams.get('orderId');

  const isSuccess = status === 'success';

  return (
    <div className="min-h-[60vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-sm border border-gray-100 text-center space-y-6">
        <div className="flex justify-center">
          {isSuccess ? (
            <CheckCircle2 className="w-20 h-20 text-green-500" />
          ) : (
            <XCircle className="w-20 h-20 text-red-500" />
          )}
        </div>
        
        <div className="space-y-2">
          <h2 className="text-2xl font-bold text-gray-900">
            {isSuccess ? 'Đặt hàng thành công!' : 'Thanh toán thất bại'}
          </h2>
          <p className="text-gray-500">
            {isSuccess 
              ? `Cảm ơn bạn đã mua sắm. Mã đơn hàng của bạn là #${orderId || 'UNKNOWN'}.` 
              : 'Đã có lỗi xảy ra trong quá trình thanh toán. Vui lòng thử lại.'}
          </p>
        </div>

        <div className="pt-6 flex flex-col gap-3">
          {isSuccess ? (
            <>
              <Link to={ROUTES.MY_ORDERS}>
                <Button className="w-full" size="lg">Xem đơn hàng</Button>
              </Link>
              <Link to={ROUTES.PRODUCTS}>
                <Button variant="secondary" className="w-full" size="lg">Tiếp tục mua sắm</Button>
              </Link>
            </>
          ) : (
            <>
              <Link to={ROUTES.CHECKOUT}>
                <Button className="w-full" size="lg">Thử lại</Button>
              </Link>
              <Link to={ROUTES.HOME}>
                <Button variant="ghost" className="w-full" size="lg">Về trang chủ</Button>
              </Link>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

export default PaymentResultPage;
