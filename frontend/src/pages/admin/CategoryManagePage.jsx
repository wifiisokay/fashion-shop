import { useState } from 'react';
import { Plus, Edit, Trash2, ChevronRight } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  useCategories, useCreateCategory, useUpdateCategory, useDeleteCategory,
} from '@/hooks/useCategories';
import Spinner from '@/components/ui/Spinner';

const CategoryManagePage = () => {
  const { data: tree = [], isLoading } = useCategories();
  const createMutation = useCreateCategory();
  const updateMutation = useUpdateCategory();
  const deleteMutation = useDeleteCategory();

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingCat, setEditingCat] = useState(null);
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [formData, setFormData] = useState({ name: '', description: '', parentId: '' });

  // === Flatten tree thành list hiển thị có indent ===
  const flatList = [];
  tree.forEach((root) => {
    flatList.push({ ...root, level: 0, childCount: root.children?.length ?? 0 });
    (root.children || []).forEach((child) => {
      flatList.push({ ...child, level: 1, childCount: 0, parentName: root.name });
    });
  });

  // Lấy danh sách root categories (cho Select parentId)
  const rootCategories = tree.filter((c) => !c.parentId);

  const handleOpenDialog = (cat = null) => {
    setEditingCat(cat);
    setFormData(
      cat
        ? { name: cat.name, description: cat.description || '', parentId: cat.parentId?.toString() || '' }
        : { name: '', description: '', parentId: '' }
    );
    setIsDialogOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      name: formData.name.trim(),
      description: formData.description.trim() || null,
      parentId: formData.parentId ? parseInt(formData.parentId) : null,
    };

    try {
      if (editingCat) {
        await updateMutation.mutateAsync({ id: editingCat.id, ...payload });
        toast.success('Cập nhật danh mục thành công');
      } else {
        await createMutation.mutateAsync(payload);
        toast.success('Thêm danh mục thành công');
      }
      setIsDialogOpen(false);
    } catch (err) {
      const msg = err.response?.data?.message || 'Có lỗi xảy ra';
      toast.error(msg);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      toast.success('Xóa danh mục thành công');
    } catch (err) {
      const msg = err.response?.data?.message || 'Không thể xóa danh mục';
      toast.error(msg);
    } finally {
      setDeleteTarget(null);
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center py-20">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Quản lý Danh mục</h1>
        <Button className="flex items-center gap-2" onClick={() => handleOpenDialog()}>
          <Plus className="w-4 h-4" /> Thêm danh mục
        </Button>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-16">ID</TableHead>
              <TableHead>Tên danh mục</TableHead>
              <TableHead>Slug</TableHead>
              <TableHead>Mô tả</TableHead>
              <TableHead className="w-32 text-center">Danh mục con</TableHead>
              <TableHead className="w-28 text-right">Hành động</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {flatList.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8 text-gray-500">
                  Không có danh mục nào
                </TableCell>
              </TableRow>
            ) : (
              flatList.map((cat) => (
                <TableRow key={cat.id} className={cat.level === 1 ? 'bg-gray-50/50' : ''}>
                  <TableCell className="font-medium text-gray-500">#{cat.id}</TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2" style={{ paddingLeft: cat.level * 24 }}>
                      {cat.level === 1 && <ChevronRight className="w-3 h-3 text-gray-400" />}
                      <span className="font-medium text-gray-900">{cat.name}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary" className="font-mono text-xs">{cat.slug}</Badge>
                  </TableCell>
                  <TableCell className="text-gray-500 max-w-xs truncate">
                    {cat.description || '—'}
                  </TableCell>
                  <TableCell className="text-center">
                    {cat.level === 0 && cat.childCount > 0 && (
                      <Badge variant="outline">{cat.childCount}</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-blue-600 hover:bg-blue-50"
                        onClick={() => handleOpenDialog(cat)}>
                        <Edit className="w-4 h-4" />
                      </Button>
                      <Button variant="ghost" size="icon" className="h-8 w-8 text-red-600 hover:bg-red-50"
                        onClick={() => setDeleteTarget(cat)}>
                        <Trash2 className="w-4 h-4" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Create/Edit Dialog */}
      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingCat ? 'Chỉnh sửa danh mục' : 'Thêm danh mục mới'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="cat-name">Tên danh mục *</Label>
              <Input
                id="cat-name"
                required
                value={formData.name}
                onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                placeholder="Nhập tên danh mục"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="cat-parent">Danh mục cha</Label>
              <Select
                value={formData.parentId}
                onValueChange={(val) => setFormData({ ...formData, parentId: val === '_none' ? '' : val })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Không có (danh mục gốc)" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="_none">Không có (danh mục gốc)</SelectItem>
                  {rootCategories.map((root) => (
                    <SelectItem key={root.id} value={root.id.toString()}>
                      {root.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="cat-desc">Mô tả</Label>
              <Textarea
                id="cat-desc"
                rows={3}
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                placeholder="Mô tả ngắn gọn..."
              />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setIsDialogOpen(false)}>
                Hủy
              </Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {(createMutation.isPending || updateMutation.isPending) && (
                  <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                )}
                {editingCat ? 'Cập nhật' : 'Thêm mới'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa danh mục "{deleteTarget?.name}"?</AlertDialogTitle>
            <AlertDialogDescription>
              Hành động này không thể hoàn tác. Các danh mục con (nếu có) sẽ trở thành danh mục gốc.
              Không thể xóa nếu danh mục đang có sản phẩm.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              className="bg-red-600 hover:bg-red-700"
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? 'Đang xóa...' : 'Xóa'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default CategoryManagePage;
