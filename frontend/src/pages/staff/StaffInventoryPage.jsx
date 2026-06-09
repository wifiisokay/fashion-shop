import React, { useState } from 'react';
import { Search, ArrowUp, ArrowDown, ArrowUpDown } from 'lucide-react';
import { useStaffInventory } from '@/hooks/useStaffInventory';
import { useCategories } from '@/hooks/useCategories';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/Button';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import Spinner from '@/components/ui/Spinner';

const STOCK_STATUS_OPTIONS = [
  { value: '_all', label: 'Tất cả trạng thái kho' },
  { value: 'IN_STOCK', label: 'Còn hàng' },
  { value: 'LOW_STOCK', label: 'Tồn kho thấp / Sắp hết' },
  { value: 'OUT_OF_STOCK', label: 'Hết hàng' },
];

const GENDER_OPTIONS = [
  { value: '_all', label: 'Tất cả giới tính' },
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'UNISEX', label: 'Unisex' },
];

const getVariantStockStatus = (stockQuantity, lowStockThreshold) => {
  if (stockQuantity === 0) {
    return {
      label: 'Hết hàng',
      badgeClass: 'bg-red-50 text-red-700 border-red-200 font-bold',
    };
  }

  if (lowStockThreshold !== undefined && lowStockThreshold !== null) {
    if (stockQuantity <= lowStockThreshold) {
      return {
        label: 'Tồn kho thấp',
        badgeClass: 'bg-amber-50 text-amber-700 border-amber-200 font-bold',
      };
    }
    return {
      label: 'Còn hàng',
      badgeClass: 'bg-green-50 text-green-700 border-green-200 font-bold',
    };
  } else {
    if (stockQuantity <= 5) {
      return {
        label: 'Sắp hết',
        badgeClass: 'bg-orange-50 text-orange-700 border-orange-200 font-bold',
      };
    }
    if (stockQuantity <= 10) {
      return {
        label: 'Tồn kho thấp',
        badgeClass: 'bg-amber-50 text-amber-700 border-amber-200 font-bold',
      };
    }
    return {
      label: 'Còn hàng',
      badgeClass: 'bg-green-50 text-green-700 border-green-200 font-bold',
    };
  }
};

const StaffInventoryPage = () => {
  const [filters, setFilters] = useState({
    keyword: '',
    categoryId: '',
    gender: '',
    status: '',
    sortBy: '',
    sortDir: '',
    page: 0,
    size: 20,
  });

  const { data, isLoading, isError } = useStaffInventory({
    keyword: filters.keyword || undefined,
    categoryId: filters.categoryId || undefined,
    gender: filters.gender || undefined,
    status: filters.status || undefined,
    sortBy: filters.sortBy || undefined,
    sortDir: filters.sortDir || undefined,
    page: filters.page,
    size: filters.size,
  });

  const { data: categories = [] } = useCategories();

  const items = data?.items ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = data?.totalPages ?? 0;

  // Flatten categories for select dropdown
  const categoryOptions = [];
  categories.forEach((root) => {
    categoryOptions.push({ value: root.id.toString(), label: root.name });
    (root.children || []).forEach((child) => {
      categoryOptions.push({ value: child.id.toString(), label: `  └ ${child.name}` });
    });
  });

  const handleFilterChange = (key, value) => {
    setFilters((prev) => ({
      ...prev,
      [key]: value,
      page: 0, // Reset to first page on filter change
    }));
  };

  const handleSort = (field) => {
    setFilters((prev) => {
      let nextDir = 'asc';
      if (prev.sortBy === field) {
        if (prev.sortDir === 'asc') {
          nextDir = 'desc';
        } else {
          return {
            ...prev,
            sortBy: '',
            sortDir: '',
            page: 0,
          };
        }
      }
      return {
        ...prev,
        sortBy: field,
        sortDir: nextDir,
        page: 0,
      };
    });
  };

  const renderSortableHeader = (field, label) => {
    const isSorted = filters.sortBy === field;
    const isAsc = filters.sortDir === 'asc';

    return (
      <TableHead
        className="cursor-pointer select-none hover:bg-gray-50/80 transition-colors py-3"
        onClick={() => handleSort(field)}
      >
        <div className="flex items-center gap-1.5 font-bold text-gray-700">
          <span>{label}</span>
          {isSorted ? (
            isAsc ? (
              <ArrowUp className="w-3.5 h-3.5 text-black shrink-0" />
            ) : (
              <ArrowDown className="w-3.5 h-3.5 text-black shrink-0" />
            )
          ) : (
            <ArrowUpDown className="w-3.5 h-3.5 text-gray-300 group-hover:text-gray-400 shrink-0" />
          )}
        </div>
      </TableHead>
    );
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Theo dõi Tồn kho</h1>
          <p className="text-sm text-gray-500">Tra cứu số lượng tồn kho chi tiết theo từng biến thể (Màu sắc & Size) của sản phẩm.</p>
        </div>
      </div>

      {/* Search and Filter Panel */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-4 border-b border-gray-200 flex flex-wrap gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <Input
              placeholder="Nhập tên sản phẩm..."
              value={filters.keyword}
              onChange={(e) => handleFilterChange('keyword', e.target.value)}
              className="pl-9"
            />
          </div>
          <Select
            value={filters.categoryId || '_all'}
            onValueChange={(val) => handleFilterChange('categoryId', val === '_all' ? '' : val)}
          >
            <SelectTrigger className="w-[180px]"><SelectValue placeholder="Danh mục" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="_all">Tất cả danh mục</SelectItem>
              {categoryOptions.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={filters.status || '_all'}
            onValueChange={(val) => handleFilterChange('status', val === '_all' ? '' : val)}
          >
            <SelectTrigger className="w-[200px]"><SelectValue placeholder="Trạng thái kho" /></SelectTrigger>
            <SelectContent>
              {STOCK_STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={filters.gender || '_all'}
            onValueChange={(val) => handleFilterChange('gender', val === '_all' ? '' : val)}
          >
            <SelectTrigger className="w-[150px]"><SelectValue placeholder="Giới tính" /></SelectTrigger>
            <SelectContent>
              {GENDER_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Table Content */}
        {isLoading ? (
          <div className="flex items-center justify-center py-16"><Spinner /></div>
        ) : isError ? (
          <div className="flex flex-col items-center justify-center py-16 text-red-500 gap-2">
            <span className="font-semibold text-lg">Đã xảy ra lỗi</span>
            <span className="text-sm">Không thể tải dữ liệu tồn kho. Vui lòng thử lại sau.</span>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-20 font-bold text-gray-700">Ảnh</TableHead>
                  {renderSortableHeader('productName', 'Tên sản phẩm')}
                  {renderSortableHeader('categoryName', 'Danh mục')}
                  {renderSortableHeader('colorName', 'Màu sắc')}
                  {renderSortableHeader('size', 'Size')}
                  {renderSortableHeader('stockQuantity', 'Số lượng tồn')}
                  {renderSortableHeader('lowStockThreshold', 'Ngưỡng cảnh báo')}
                  {renderSortableHeader('status', 'Trạng thái kho')}
                </TableRow>
              </TableHeader>
              <TableBody>
                {items.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-12 text-gray-500">
                      Không có biến thể nào phù hợp
                    </TableCell>
                  </TableRow>
                ) : (
                  items.map((item, index) => {
                    const badgeProps = getVariantStockStatus(item.stockQuantity, item.lowStockThreshold);
                    return (
                      <TableRow key={`${item.productId}-${item.colorName}-${item.size}-${index}`} className="hover:bg-gray-50/50 transition-colors">
                        <TableCell>
                          {item.imageUrl ? (
                            <img
                              src={item.imageUrl}
                              alt={item.productName}
                              className="w-10 h-10 object-cover rounded-md border border-gray-200"
                            />
                          ) : (
                            <div className="w-10 h-10 bg-gray-100 rounded-md flex items-center justify-center text-[10px] text-gray-400 border border-gray-200">
                              No img
                            </div>
                          )}
                        </TableCell>
                        <TableCell className="font-semibold text-gray-900">{item.productName}</TableCell>
                        <TableCell>
                          <Badge variant="secondary" className="font-normal">{item.categoryName}</Badge>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2">
                            <span
                              className="w-3.5 h-3.5 rounded-full border border-gray-300 shadow-sm shrink-0"
                              style={{ backgroundColor: item.colorCode }}
                            />
                            <span className="text-gray-700 text-sm font-medium">{item.colorName}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline" className="px-2 py-0.5 font-bold text-xs">{item.size}</Badge>
                        </TableCell>
                        <TableCell className="font-bold text-gray-950 text-sm">{item.stockQuantity}</TableCell>
                        <TableCell className="text-gray-500 font-medium">
                          {item.lowStockThreshold !== undefined && item.lowStockThreshold !== null
                            ? item.lowStockThreshold
                            : 'Mặc định (10/5)'}
                        </TableCell>
                        <TableCell>
                          <Badge className={badgeProps.badgeClass} variant="outline">
                            {badgeProps.label}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        )}

        {/* Pagination Section */}
        {!isLoading && !isError && totalPages > 1 && (
          <div className="flex items-center justify-between p-4 border-t border-gray-200">
            <span className="text-sm text-gray-500">
              Trang {filters.page + 1} / {totalPages} (Tổng số: {totalElements} biến thể)
            </span>
            <div className="flex gap-2">
              <Button variant="outline" size="sm"
                disabled={filters.page === 0}
                onClick={() => setFilters((f) => ({ ...f, page: f.page - 1 }))}>
                Trước
              </Button>
              <Button variant="outline" size="sm"
                disabled={filters.page + 1 >= totalPages}
                onClick={() => setFilters((f) => ({ ...f, page: f.page + 1 }))}>
                Sau
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StaffInventoryPage;
