import React from 'react';
import { Link } from 'react-router-dom';

const NotFoundPage = () => {
  return (
    <div className="min-h-[70vh] flex flex-col items-center justify-center text-center px-4 py-16">
      <div className="text-9xl font-extrabold text-primary tracking-widest">404</div>
      <div className="bg-primary text-primary-foreground px-3 py-1 text-sm rounded rotate-12 absolute font-semibold">
        Không tìm thấy trang
      </div>
      <h2 className="text-3xl font-bold mt-8 mb-4 text-foreground">Đường dẫn không tồn tại</h2>
      <p className="text-muted-foreground mb-8 max-w-md">
        Trang bạn đang tìm kiếm có thể đã bị xóa, thay đổi tên hoặc tạm thời không khả dụng.
      </p>
      <Link
        to="/"
        className="inline-flex items-center justify-center px-6 py-3 border border-transparent text-base font-medium rounded-md shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 transition-colors"
      >
        Quay lại Trang chủ
      </Link>
    </div>
  );
};

export default NotFoundPage;
