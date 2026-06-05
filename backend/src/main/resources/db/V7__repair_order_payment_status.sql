UPDATE orders o
JOIN payments p ON p.order_id = o.id
SET o.payment_status = 'PAID'
WHERE p.status = 'SUCCESS'
  AND o.payment_status = 'UNPAID';

UPDATE orders o
JOIN payments p ON p.order_id = o.id
SET o.payment_status = 'REFUNDED'
WHERE p.status = 'REFUNDED'
  AND o.payment_status <> 'REFUNDED';
