# Báo Cáo Cấu Trúc Frontend - Fashion Shop

Tài liệu này tổng hợp cấu trúc hiện tại của dự án Frontend `fashion-shop`, các công nghệ đang được sử dụng và tổng quan các luồng tính năng đã được implement dựa trên mã nguồn thực tế.

## 1. Công Nghệ Xây Dựng (Tech Stack)

Dựa vào `package.json`, frontend đang được xây dựng theo architecture khá hiện đại và tiêu chuẩn:
* **Core Framework:** React 19 kết hợp Build tool Vite (v8)
* **Styling & UI:** Tailwind CSS v4 phối hợp với `clsx` và `tailwind-merge` để xử lý linh hoạt ClassName UI component theo chuẩn UI Shadcn-style.
* **Routing:** `react-router-dom` v6
* **Data Fetching / State:** Sử dụng `@tanstack/react-query` kết hợp với custom hooks. Backend call bằng `axios`.
* **Form & Validation:** `react-hook-form` phối hợp trơn tru với `zod` để validate an toàn.
* **Utility Libraries:** `dayjs` (xử lý ngày giờ), `lucide-react` (SVG Icons), `react-markdown` (render chuỗi markdown).

## 2. Tổ Gửi Cấu Trúc Thư Mục (Directory Structure)

Toàn bộ logic nghiệp vụ nằm trong thư mục `src/`, được tổ chức chặt chẽ theo hướng module và tính năng (Feature-based x Role-based):

```text
src/
├── api/             # Nơi config axios và định nghĩa các service call API
│   ├── axiosInstance.js
│   ├── authApi.js, cartApi.js, productApi.js, orderApi.js...
├── components/      # Các React Components chia theo domain và cấp độ
│   ├── ui/          # Core Component tái sử dụng cao (Button, Spinner...)
│   ├── common/      # Layout chung (MainLayout, Navigation...)
│   ├── admin/, customer/, staff/ # Component phân mảnh theo loại User
│   └── chat/, order/, product/   # Component phân theo tính năng cụ thể
├── contexts/        # React Context duy trì Global State
│   └── AuthContext.jsx # Quản lý trạng thái đăng nhập, Role của user
├── pages/           # Tập hợp các màn hình gốc của ứng dụng
│   ├── auth/        # Login, Register, Forgot/Reset Password
│   ├── customer/    # Trang KH (Trang chủ, SP, Giỏ hàng, Đơn hàng, Checkout)
│   ├── admin/       # Dashboard, Manage Product, Category, User
│   └── staff/       # Quản lý Orders, Handles Returns (Đổi trả)
├── hooks/           # Các Custom hook đóng gói logic (chủ yếu là React Query)
│   ├── useAuth.js, useCart.js, useProducts.js, useAddresses.js...
├── routes/          # Cấu hình kiến trúc định tuyến của app
│   ├── AppRouter.jsx    # Tổng hợp các route
│   ├── PrivateRoute.jsx # Guard bảo vệ route cần đăng nhập
│   └── RoleRoute.jsx    # Guard bảo vệ route dựa trên loại account (Customer/Staff/Admin)
└── utils/           # Helper functions thuần js
    ├── cn.js        # Merge Tailwind classes
    └── format.js    # Tiện ích format Tiền tệ, Ngày tháng
```

## 3. Tổng Quan Tính Năng Đã Hoàn Thiện (Implemented Features)

Các bộ tính năng đã được dựng sẵn Front-end hiện tại hướng đến mô hình thương mại điện tử trọn vẹn:

### A. Hệ thống Auth & Bảo Mật (Authentication)
* Luồng Login, Register.
* Forgot Password & Khôi phục (Reset) Password.
* Middleware Route bảo vệ (`PrivateRoute`, `RoleRoute`) để khóa và điều hướng chặt chẽ các trang tương ứng với Role của User.

### B. Module Khách Hàng (Customer Features)
* **Mua sắm:** Xem Product List, Xem Product Detail, thêm vào Giỏ hàng (Cart).
* **Thanh toán:** Cổng Checkout thông tin và trang Payment Result.
* **Quản lý Cá nhân:** Trang cá nhân (Profile Page), tính năng chọn định vị địa chỉ (useAddresses).
* **Quản lý Đơn hàng:** Xem lịch sử mua (Order List) và chi tiết (Order Detail).
* **Tích cực tương tác:** (Dự phòng cho luồng Chat - `useChatMessages.js`, `chatApi.js`).

### C. Module Nhân Viên (Staff Features)
* Danh sách Đơn hàng của toàn hệ thống (`StaffOrderListPage`).
* Chi tiết đơn và xử lý trạng thái (`StaffOrderDetailPage`).
* Đặc biệt: Quản lý Yêu cầu Đổi / Trả hàng hóa (`StaffReturnManagePage`).

### D. Module Quản Trị Viên (Admin Features)
* Kiểm soát và Thống kê tổng quan (`DashboardPage`).
* Quản lý CRUD thư mục Sản Phẩm (`ProductManagePage`, `ProductFormPage`).
* Quản lý Categories (`CategoryManagePage`).
* Phân quyền và Quản lý Users hệ thống (`UserManagePage`).

### 4. Đánh giá kỹ thuật
* **Modular rất cao:** Việc tách router (`routes`), page riêng các folder role (`admin`/`staff`/`customer`), api call và react-query tách bạch (qua thư mục `hooks`) giúp mã nguồn sạch sẻ và rất dễ bảo trì.
* Hệ thống UI đã được chuẩn bị base `Tailwind` khá kỹ (`components/ui` và hàm `cn` pattern giống hướng dẫn của shadcn/ui).
