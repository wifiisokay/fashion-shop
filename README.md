# Fashion Shop

Fashion Shop is a full-stack fashion e-commerce project built with React and Spring Boot. The system supports product variants by color/size, cart and selected-item checkout, order processing, VNPay payment, GHN shipping fee calculation, return/refund workflow, admin dashboard, and an AI fashion assistant.

## Main Features

- Customer catalog with product search, filters, sale price, color, size, stock, reviews, and outfit suggestions.
- Cart with selected-item checkout, so users can pay for only part of the cart while keeping the rest.
- Order flow for COD and VNPay:
  - COD orders start at `PENDING`.
  - VNPay orders start at `AWAITING_PAYMENT` and move to `PENDING` after successful payment.
- VNPay Sandbox integration with signed payment URL, IPN callback, Return URL fallback, idempotent payment update, and simulated refund in DB.
- GHN Sandbox integration for province/district/ward master data and shipping fee calculation.
- Staff/admin order management with confirmation, packing, shipping, cancellation rules, and admin completion.
- Return workflow: `REQUESTED -> APPROVED/REJECTED -> RECEIVED -> REFUNDED`.
- Admin dashboard with order, revenue, return, stock, and product statistics.
- AI chatbot for customers using Gemini, SQL-based retrieval, recent session context, and Redis outfit cache.
- JWT authentication using HttpOnly cookie, token blacklist, `tokenVersion`, role-based authorization, and rate limiting for sensitive auth endpoints.
- Cloudinary image upload for product images, avatars, and return evidence.

## Tech Stack

### Backend

- Java 21
- Spring Boot 3.4.4
- Spring Web, Spring Security, Spring Data JPA, Validation, Mail, Actuator
- MySQL
- Redis
- Caffeine cache
- JJWT
- Cloudinary SDK
- Springdoc OpenAPI / Swagger UI
- Maven Wrapper

### Frontend

- React 19
- Vite
- React Router
- TanStack React Query
- Axios
- React Hook Form + Zod
- Radix UI
- Recharts
- Lucide React
- Sonner

### External Services

- VNPay Sandbox
- GHN Sandbox
- Google Gemini API
- Cloudinary
- Redis
- MySQL

## Project Structure

```text
fashion-shop/
  backend/
    src/main/java/com/fashionshop/backend/
      module/
        ai/
        auth/
        cart/
        dashboard/
        order/
        payment/
        product/
        returnrequest/
        review/
        shipping/
        user/
      domain/
      config/
      common/
    src/main/resources/
      application.properties
      db/
    pom.xml
  frontend/
    src/
    public/
    package.json
  README.md
```

## Requirements

- Java 21
- Node.js 20+ recommended
- MySQL 8+
- Redis
- Maven Wrapper, already included in `backend`

## Backend Setup

1. Create a MySQL database, for example:

```sql
CREATE DATABASE fashion_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. Create `backend/.env` from the local template:

```powershell
Copy-Item backend/.env.local.example backend/.env
```

3. Configure the required environment variables in `backend/.env`.

Important variables:

```properties
DB_URL=jdbc:mysql://localhost:3306/fashion_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
DB_USERNAME=your_db_username
DB_PASSWORD=your_db_password

JWT_SECRET=your_32_bytes_or_longer_secret
AUTH_COOKIE_NAME=access_token
AUTH_COOKIE_SECURE=false
AUTH_COOKIE_SAME_SITE=Lax
SERVER_SSL_ENABLED=false

CORS_ALLOWED_ORIGINS=http://localhost:5173,https://localhost:5173
APP_FRONTEND_URL=http://localhost:5173

EMAIL_USERNAME=your_email
EMAIL_PASSWORD=your_app_password
AUTH_MAIL_ENABLED=true

CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

GHN_TOKEN=your_ghn_token
GHN_SHOP_ID=your_ghn_shop_id
GHN_BASE_URL=https://dev-online-gateway.ghn.vn/shiip/public-api

VNPAY_TMN_CODE=your_vnpay_tmn_code
VNPAY_HASH_SECRET=your_vnpay_hash_secret
VNPAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=http://localhost:8081/api/payment/vnpay-return
VNPAY_IPN_URL=

GEMINI_API_KEY=your_gemini_api_key
GEMINI_MODEL=gemini-3.1-flash-lite

REDIS_URL=redis://localhost:6379
```

Do not commit real secrets to Git.

4. Run the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Default backend URL:

```text
http://localhost:8081
```

Swagger UI:

```text
http://localhost:8081/swagger-ui.html
```

Health check:

```text
http://localhost:8081/api/health
```

## Frontend Setup

1. Install dependencies:

```powershell
cd frontend
npm install
```

2. Create local environment file from the example:

```powershell
Copy-Item .env.example .env.development
```

3. Configure the API base URL:

```env
VITE_API_BASE_URL=http://localhost:8081
VITE_CLOUDINARY_CLOUD_NAME=your_cloud_name
VITE_VNPAY_RETURN_URL=http://localhost:5173/payment/result
VITE_GHN_SHOP_ID=your_shop_id
VITE_GEMINI_API_KEY=
```

4. Start the frontend:

```powershell
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

## Common Commands

Backend:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Frontend:

```powershell
cd frontend
npm run dev
npm run build
npm run lint
npm run preview
```

## Key Business Rules

- Backend recalculates product price, shipping fee, subtotal, and total amount. It does not trust frontend totals.
- Product variants are not hard-deleted when referenced by cart, order, or return data. Use stock `0` or product `INACTIVE` instead.
- VNPay paid orders in `PENDING` cannot be rejected by staff/admin at the confirmation step.
- VNPay refund is currently simulated in the database. The project does not call the production VNPay Refund API yet.
- GHN integration currently calculates shipping fees and loads address master data. It does not create real shipping orders or tracking codes.
- Return stock is restored only when admin confirms returned goods as received.
- Chatbot is shown only to authenticated customers, not admin/staff users.
- AI outfit suggestions use Redis cache and lock to reduce repeated Gemini calls.

## Main API Groups

- Auth: `/api/auth`
- User profile: `/api/user`
- User addresses: `/api/user/addresses`
- Products: `/api/products`
- Admin products: `/api/admin/products`
- Cart: `/api/cart`
- Orders: `/api/orders`
- Staff orders: `/api/staff/orders`
- Shipping: `/api/shipping`
- GHN master data: `/api/ghn`
- Payment: `/api/payment`
- Admin payments: `/api/admin/payments`
- Returns: `/api/returns`
- Staff returns: `/api/staff/returns`
- Admin returns: `/api/admin/returns`
- Chatbot: `/api/chat`
- Admin chat: `/api/admin/chat`
- Dashboard: `/api/admin/dashboard`

## Deployment Notes

### Backend on Render

Recommended production settings:

```properties
SERVER_SSL_ENABLED=false
AUTH_COOKIE_SECURE=true
AUTH_COOKIE_SAME_SITE=None
CORS_ALLOWED_ORIGINS=https://your-frontend.vercel.app
APP_FRONTEND_URL=https://your-frontend.vercel.app
```

Render usually handles HTTPS at the proxy/load balancer layer, so Spring Boot SSL should be disabled in production.

### Frontend on Vercel

Set:

```env
VITE_API_BASE_URL=https://your-backend.onrender.com
VITE_VNPAY_RETURN_URL=https://your-frontend.vercel.app/payment/result
```

For stable cookie-based login across Vercel and Render:

- Frontend requests must use `withCredentials: true`.
- Backend CORS must use exact allowed origins, not `*`.
- Backend CORS must allow credentials.
- Cookie should use `Secure=true`.
- If frontend and backend are cross-site, use `SameSite=None`.

## Security Notes

- Keep all secrets out of Git.
- Rotate exposed secrets if they were ever committed to a public repository.
- Use strong `JWT_SECRET`.
- Do not expose VNPay hash secret, Cloudinary API secret, GHN token, database password, or Gemini API key to the frontend.
- Public payment status endpoint returns only minimal status and does not expose amount, bank code, or transaction reference.

## Current Limitations

- VNPay refund is simulated in DB and not integrated with the real VNPay Refund API.
- GHN is used for fee calculation and master data only, not real order creation/tracking.
- AI search is SQL/rule-based retrieval plus Gemini prompting, not vector-based RAG.
- Full audit log for sensitive staff/admin actions is not implemented yet.
- Some flows can benefit from additional integration and frontend tests.

## License

This project is intended for academic and demonstration purposes.
