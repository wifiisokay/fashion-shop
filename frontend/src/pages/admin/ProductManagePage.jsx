import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, Edit, Search, Power } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useAdminProducts, useUpdateProductStatus } from '@/hooks/useAdminProducts';
import { useCategories } from '@/hooks/useCategories';
import { formatPrice } from '@/utils/format';
import Spinner from '@/components/ui/Spinner';

const STATUS_OPTIONS = [
  { value: '_all', label: 'Tất cả' },
  { value: 'ACTIVE', label: 'Đang bán' },
  { value: 'INACTIVE', label: 'Ngừng bán' },
];

const GENDER_OPTIONS = [
  { value: '_all', label: 'Tất cả' },
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'UNISEX', label: 'Unisex' },
];

const ProductManagePage = () => {
  const [filters, setFilters] = useState({
    keyword: '', categoryId: '', status: '', gender: '', page: 0, size: 20,
  });
  const [statusToggle, setStatusToggle] = useState(null); // { id, currentStatus }

  const params = {
    ...filters,
    categoryId: filters.categoryId || undefined,
    status: filters.status || undefined,
    gender: filters.gender || undefined,
    keyword: filters.keyword || undefined,
  };

  const { data, isLoading } = useAdminProducts(params);
  const { data: categories = [] } = useCategories();
  const statusMutation = useUpdateProductStatus();

  const products = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  // Flatten categories for select
  const categoryOptions = [];
  categories.forEach((root) => {
    categoryOptions.push({ value: root.id.toString(), label: root.name });
    (root.children || []).forEach((child) => {
      categoryOptions.push({ value: child.id.toString(), label: `  └ ${child.name}` });
    });
  });

  const handleToggleStatus = async () => {
    if (!statusToggle) return;
    const newStatus = statusToggle.currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    try {
      await statusMutation.mutateAsync({ id: statusToggle.id, status: newStatus });
      toast.success(`Đã chuyển trạng thái sang ${newStatus === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}`);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Có lỗi xảy ra');
    } finally {
      setStatusToggle(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Sản phẩm</h1>
        <Link to="/admin/products/form">
          <Button className="flex items-center gap-2">
            <Plus className="w-4 h-4" /> Thêm sản phẩm
          </Button>
        </Link>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-4 rounded-xl shadow-sm border border-gray-200">
          <p className="text-sm font-medium text-gray-500">Tổng số Sản phẩm</p>
          <h3 className="text-2xl font-bold text-gray-900">{data?.totalElements || 0}</h3>
          <p className="text-xs text-gray-400 mt-1">
            {filters.categoryId ? `Trong danh mục đang chọn` : 'Trên toàn hệ thống'}
          </p>
        </div>
        <div className="bg-white p-4 rounded-xl shadow-sm border border-green-100">
          <p className="text-sm font-medium text-green-600">Đang kinh doanh</p>
          <h3 className="text-2xl font-bold text-gray-900">
            {data?.content?.filter(p => p.status === 'ACTIVE').length || 0} <span className="text-sm font-normal text-gray-500">/ trang này</span>
          </h3>
        </div>
        <div className="bg-white p-4 rounded-xl shadow-sm border border-red-100">
          <p className="text-sm font-medium text-red-600">Ngừng kinh doanh</p>
          <h3 className="text-2xl font-bold text-gray-900">
            {data?.content?.filter(p => p.status === 'INACTIVE').length || 0} <span className="text-sm font-normal text-gray-500">/ trang này</span>
          </h3>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="p-4 border-b border-gray-200 flex flex-wrap gap-3">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <Input
              placeholder="Tìm kiếm sản phẩm..."
              value={filters.keyword}
              onChange={(e) => setFilters((f) => ({ ...f, keyword: e.target.value, page: 0 }))}
              className="pl-9"
            />
          </div>
          <Select
            value={filters.categoryId || '_all'}
            onValueChange={(val) => setFilters((f) => ({ ...f, categoryId: val === '_all' ? '' : val, page: 0 }))}
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
            onValueChange={(val) => setFilters((f) => ({ ...f, status: val === '_all' ? '' : val, page: 0 }))}
          >
            <SelectTrigger className="w-[150px]"><SelectValue placeholder="Trạng thái" /></SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={filters.gender || '_all'}
            onValueChange={(val) => setFilters((f) => ({ ...f, gender: val === '_all' ? '' : val, page: 0 }))}
          >
            <SelectTrigger className="w-[130px]"><SelectValue placeholder="Giới tính" /></SelectTrigger>
            <SelectContent>
              {GENDER_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {/* Table */}
        {isLoading ? (
          <div className="flex items-center justify-center py-16"><Spinner /></div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-16">ID</TableHead>
                <TableHead>Tên sản phẩm</TableHead>
                <TableHead>Giá</TableHead>
                <TableHead>Giới tính</TableHead>
                <TableHead>Trạng thái</TableHead>
                <TableHead className="w-24 text-right">Hành động</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {products.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center py-8 text-gray-500">
                    Không tìm thấy sản phẩm nào
                  </TableCell>
                </TableRow>
              ) : (
                products.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium text-gray-500">#{p.id}</TableCell>
                    <TableCell>
                      <span className="font-medium text-gray-900">{p.name}</span>
                    </TableCell>
                    <TableCell>
                      {p.isSale && p.salePrice ? (
                        <div className="flex flex-col">
                          <span className="text-red-600 font-medium">{formatPrice(p.salePrice)}</span>
                          <span className="text-xs text-gray-400 line-through">{formatPrice(p.basePrice)}</span>
                        </div>
                      ) : (
                        <span>{formatPrice(p.basePrice)}</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant="secondary">
                        {p.gender === 'MALE' ? 'Nam' : p.gender === 'FEMALE' ? 'Nữ' : 'Unisex'}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <Badge variant={p.status === 'ACTIVE' ? 'default' : 'destructive'}>
                        {p.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex items-center justify-end gap-1">
                        <Link to={`/admin/products/form?id=${p.id}`}>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-blue-600 hover:bg-blue-50">
                            <Edit className="w-4 h-4" />
                          </Button>
                        </Link>
                        <Button variant="ghost" size="icon" className="h-8 w-8 text-orange-600 hover:bg-orange-50"
                          onClick={() => setStatusToggle({ id: p.id, currentStatus: p.status })}>
                          <Power className="w-4 h-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between p-4 border-t border-gray-200">
            <span className="text-sm text-gray-500">
              Trang {filters.page + 1} / {totalPages}
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

      {/* Status Toggle Dialog */}
      <AlertDialog open={!!statusToggle} onOpenChange={(open) => !open && setStatusToggle(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Đổi trạng thái sản phẩm?</AlertDialogTitle>
            <AlertDialogDescription>
              {statusToggle?.currentStatus === 'ACTIVE'
                ? 'Sản phẩm sẽ ngừng hiển thị trên cửa hàng.'
                : 'Sản phẩm sẽ được hiển thị lại trên cửa hàng.'}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleToggleStatus} disabled={statusMutation.isPending}>
              {statusMutation.isPending ? 'Đang xử lý...' : 'Xác nhận'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default ProductManagePage;
