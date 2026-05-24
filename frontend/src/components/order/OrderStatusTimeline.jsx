import { clsx } from 'clsx';
import { Check, X, RotateCcw } from 'lucide-react';

const MAIN_STEPS = [
  { key: 'PENDING', label: 'Chờ xác nhận' },
  { key: 'CONFIRMED', label: 'Đã xác nhận' },
  { key: 'SHIPPING', label: 'Đang giao' },
  { key: 'DELIVERED', label: 'Đã giao' },
  { key: 'COMPLETED', label: 'Hoàn thành' },
];

const RETURN_STEPS = [
  { key: 'RETURN_REQUESTED', label: 'Yêu cầu đổi/trả' },
  { key: 'RETURNING', label: 'Đang xử lý' },
  { key: 'RETURNED', label: 'Đã xử lý' },
];

const STATUS_ORDER = ['AWAITING_PAYMENT', 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'COMPLETED'];
const RETURN_ORDER = ['RETURN_REQUESTED', 'RETURNING', 'RETURNED'];

const OrderStatusTimeline = ({ status }) => {
  const isCancelled = status === 'CANCELLED';
  const isReturn = RETURN_ORDER.includes(status);
  const isAwaitingPayment = status === 'AWAITING_PAYMENT';

  const currentMainIndex = STATUS_ORDER.indexOf(status);
  const currentReturnIndex = RETURN_ORDER.indexOf(status);

  // For return statuses, the main timeline should show up to DELIVERED as completed
  const mainCompletedUpTo = isReturn ? STATUS_ORDER.indexOf('DELIVERED') : currentMainIndex;

  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6">
      {/* Main Timeline */}
      <div className="flex items-center justify-between">
        {MAIN_STEPS.map((step, idx) => {
          const stepIdx = STATUS_ORDER.indexOf(step.key);
          const isCompleted = !isCancelled && mainCompletedUpTo >= 0 && stepIdx <= mainCompletedUpTo && stepIdx < mainCompletedUpTo;
          const isCurrent = !isCancelled && stepIdx === mainCompletedUpTo;
          const isPending = !isCompleted && !isCurrent;

          return (
            <div key={step.key} className="flex items-center flex-1 last:flex-none">
              <div className="flex flex-col items-center">
                <div
                  className={clsx(
                    'w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold transition-all',
                    isCompleted && 'bg-green-500 text-white',
                    isCurrent && !isCancelled && 'bg-blue-500 text-white ring-4 ring-blue-100 animate-pulse',
                    isPending && !isCancelled && 'bg-gray-200 text-gray-400',
                    isCancelled && 'bg-gray-200 text-gray-400'
                  )}
                >
                  {isCompleted ? <Check className="w-4 h-4" /> : (idx + 1)}
                </div>
                <span className={clsx(
                  'text-xs mt-1.5 text-center whitespace-nowrap',
                  isCompleted && 'text-green-600 font-medium',
                  isCurrent && !isCancelled && 'text-blue-600 font-bold',
                  isPending && 'text-gray-400',
                  isCancelled && 'text-gray-400'
                )}>
                  {step.label}
                </span>
              </div>
              {idx < MAIN_STEPS.length - 1 && (
                <div className={clsx(
                  'flex-1 h-0.5 mx-2 mt-[-20px]',
                  !isCancelled && stepIdx < mainCompletedUpTo ? 'bg-green-400' : 'bg-gray-200'
                )} />
              )}
            </div>
          );
        })}
      </div>

      {/* Cancelled badge */}
      {isCancelled && (
        <div className="flex items-center gap-2 mt-4 p-3 bg-red-50 rounded-lg border border-red-200">
          <div className="w-6 h-6 rounded-full bg-red-500 text-white flex items-center justify-center">
            <X className="w-4 h-4" />
          </div>
          <span className="text-sm font-semibold text-red-700">Đơn hàng đã bị hủy</span>
        </div>
      )}

      {/* Awaiting Payment badge */}
      {isAwaitingPayment && (
        <div className="flex items-center gap-2 mt-4 p-3 bg-yellow-50 rounded-lg border border-yellow-200">
          <span className="text-sm font-semibold text-yellow-700">⏳ Đang chờ thanh toán VNPAY</span>
        </div>
      )}

      {/* Return Timeline */}
      {isReturn && (
        <div className="mt-4 pl-4 border-l-2 border-orange-300">
          <div className="flex items-center gap-4 py-2">
            {RETURN_STEPS.map((step, idx) => {
              const stepIdx = RETURN_ORDER.indexOf(step.key);
              const isCompleted = currentReturnIndex >= 0 && stepIdx < currentReturnIndex;
              const isCurrent = stepIdx === currentReturnIndex;

              return (
                <div key={step.key} className="flex items-center gap-2">
                  <div className={clsx(
                    'w-6 h-6 rounded-full flex items-center justify-center text-xs',
                    isCompleted && 'bg-orange-500 text-white',
                    isCurrent && 'bg-orange-500 text-white ring-4 ring-orange-100 animate-pulse',
                    !isCompleted && !isCurrent && 'bg-gray-200 text-gray-400'
                  )}>
                    {isCompleted ? <Check className="w-3 h-3" /> : <RotateCcw className="w-3 h-3" />}
                  </div>
                  <span className={clsx(
                    'text-xs whitespace-nowrap',
                    (isCompleted || isCurrent) ? 'text-orange-600 font-semibold' : 'text-gray-400'
                  )}>
                    {step.label}
                  </span>
                  {idx < RETURN_STEPS.length - 1 && (
                    <div className={clsx('w-8 h-0.5', stepIdx < currentReturnIndex ? 'bg-orange-400' : 'bg-gray-200')} />
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default OrderStatusTimeline;
