import { Link, useSearchParams } from 'react-router-dom';
import { useEffect, useState, useRef } from 'react';
import { ROUTES } from '../../constants/routes';
import Button from '../../components/ui/Button';
import Spinner from '../../components/ui/Spinner';
import { paymentApi } from '../../api/paymentApi';
import { CheckCircle2, XCircle, Clock, AlertTriangle } from 'lucide-react';

/**
 * PaymentResultPage — hiển thị kết quả thanh toán VNPay.
 *
 * Flow: VNPay Return URL → Backend verify → redirect FE về đây.
 * URL params: ?orderId=X&status=success|failed
 *
 * QUAN TRỌNG: Không tin vào URL params để hiển thị kết quả.
 * Luôn gọi API lấy trạng thái thật từ DB (IPN là nguồn sự thật).
 * Nếu IPN chưa về kịp → poll API mỗi 2s, tối đa 10 lần.
 */
const PaymentResultPage = () => {
  const [searchParams] = useSearchParams();
  const orderId = searchParams.get('orderId');

  const hasOrderId = Boolean(orderId);
  const [paymentStatus, setPaymentStatus] = useState(null); // PENDING | PAID | FAILED
  const [loading, setLoading] = useState(hasOrderId);
  const [error, setError] = useState(hasOrderId ? null : 'Không tìm thấy mã đơn hàng');
  const pollCountRef = useRef(0);
  const pollTimerRef = useRef(null);

  useEffect(() => {
    if (!orderId) return;

    const checkPayment = async () => {
      try {
        // Dùng public endpoint (không cần auth) vì JWT có thể bị mất sau VNPay redirect
        const { data } = await paymentApi.getPaymentStatus(orderId);
        const status = data.data?.status;

        if (status === 'SUCCESS' || status === 'FAILED' || status === 'REFUNDED') {
          // Trạng thái cuối — dừng poll
          setPaymentStatus(status);
          setLoading(false);
          return;
        }

        // Vẫn PENDING — IPN chưa về kịp, poll tiếp
        pollCountRef.current += 1;
        if (pollCountRef.current >= 10) {
          // Hết 10 lần poll (20s) — hiện trạng thái hiện tại
          setPaymentStatus(status || 'PENDING');
          setLoading(false);
          return;
        }

        // Poll lại sau 2s
        pollTimerRef.current = setTimeout(checkPayment, 2000);
      } catch {
        setError('Không thể kiểm tra trạng thái thanh toán');
        setLoading(false);
      }
    };

    checkPayment();

    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, [orderId]);

  // Loading state
  if (loading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center py-12 px-4">
        <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-sm border border-gray-100 text-center space-y-6">
          <Spinner size="lg" />
          <div className="space-y-2">
            <h2 className="text-xl font-semibold text-gray-900">
              Đang xác nhận thanh toán...
            </h2>
            <p className="text-gray-500 text-sm">
              Vui lòng đợi trong giây lát, hệ thống đang xử lý giao dịch.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (error) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center py-12 px-4">
        <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-sm border border-gray-100 text-center space-y-6">
          <AlertTriangle className="w-20 h-20 text-yellow-500 mx-auto" />
          <h2 className="text-xl font-semibold text-gray-900">{error}</h2>
          <Link to={ROUTES.HOME}>
            <Button variant="secondary" className="w-full" size="lg">Về trang chủ</Button>
          </Link>
        </div>
      </div>
    );
  }

  const isSuccess = paymentStatus === 'SUCCESS';
  const isPending = paymentStatus === 'PENDING';

  return (
    <div className="min-h-[60vh] flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full bg-white p-8 rounded-3xl shadow-sm border border-gray-100 text-center space-y-6">
        <div className="flex justify-center">
          {isSuccess && <CheckCircle2 className="w-20 h-20 text-green-500" />}
          {isPending && <Clock className="w-20 h-20 text-yellow-500" />}
          {!isSuccess && !isPending && <XCircle className="w-20 h-20 text-red-500" />}
        </div>

        <div className="space-y-2">
          <h2 className="text-2xl font-bold text-gray-900">
            {isSuccess && 'Thanh toán thành công!'}
            {isPending && 'Đang xử lý thanh toán'}
            {!isSuccess && !isPending && 'Thanh toán thất bại'}
          </h2>
          <p className="text-gray-500">
            {isSuccess && `Cảm ơn bạn đã mua sắm. Mã đơn hàng của bạn là #${orderId}.`}
            {isPending && 'Giao dịch đang được xử lý. Vui lòng kiểm tra lại trong mục đơn hàng.'}
            {!isSuccess && !isPending && 'Đã có lỗi xảy ra trong quá trình thanh toán. Vui lòng thử lại.'}
          </p>
        </div>

        <div className="pt-6 flex flex-col gap-3">
          {(isSuccess || isPending) && (
            <>
              <Link to={orderId ? `${ROUTES.MY_ORDERS}/${orderId}` : ROUTES.MY_ORDERS}>
                <Button className="w-full" size="lg">Xem đơn hàng</Button>
              </Link>
              <Link to={ROUTES.PRODUCTS}>
                <Button variant="secondary" className="w-full" size="lg">Tiếp tục mua sắm</Button>
              </Link>
            </>
          )}
          {!isSuccess && !isPending && (
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
