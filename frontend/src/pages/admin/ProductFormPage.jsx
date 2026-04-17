import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Save, Plus, Trash2, Upload, Star } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '@/components/ui/table';
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useCategories } from '@/hooks/useCategories';
import { useAdminProduct, useCreateProduct, useUpdateProduct } from '@/hooks/useAdminProducts';
import { useCreateVariant, useUpdateVariant, useDeleteVariant } from '@/hooks/useAdminVariants';
import { useUploadImage, useSetPrimaryImage, useDeleteImage } from '@/hooks/useAdminImages';
import Spinner from '@/components/ui/Spinner';

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'UNISEX', label: 'Unisex' },
];

const ProductFormPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('id');
  const isEdit = !!productId;

  // Data fetching
  const { data: product, isLoading: loadingProduct } = useAdminProduct(productId);
  const { data: categories = [] } = useCategories();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const createVariant = useCreateVariant();
  const updateVariantMutation = useUpdateVariant();
  const deleteVariant = useDeleteVariant();
  const uploadImage = useUploadImage();
  const setPrimary = useSetPrimaryImage();
  const deleteImage = useDeleteImage();

  // Form state
  const [form, setForm] = useState({
    name: '', description: '', basePrice: '', salePrice: '',
    isSale: false, gender: 'MALE', material: '', colorFamily: '',
    categoryId: '', styleTags: '', occasionTags: '',
  });

  // Variant dialog state
  const [variantDialog, setVariantDialog] = useState(false);
  const [editVariant, setEditVariant] = useState(null);
  const [variantForm, setVariantForm] = useState({ color: '', size: '', stockQuantity: 0, price: '' });
  const [deleteVariantTarget, setDeleteVariantTarget] = useState(null);
  const [deleteImageTarget, setDeleteImageTarget] = useState(null);

  // Flatten categories
  const categoryOptions = [];
  categories.forEach((root) => {
    categoryOptions.push({ value: root.id.toString(), label: root.name });
    (root.children || []).forEach((child) => {
      categoryOptions.push({ value: child.id.toString(), label: `  └ ${child.name}` });
    });
  });

  // Populate form when editing
  useEffect(() => {
    if (product && isEdit) {
      setForm({
        name: product.name || '',
        description: product.description || '',
        basePrice: product.basePrice?.toString() || '',
        salePrice: product.salePrice?.toString() || '',
        isSale: product.isSale || false,
        gender: product.gender || 'MALE',
        material: product.material || '',
        colorFamily: product.colorFamily || '',
        categoryId: product.categoryId?.toString() || '',
        styleTags: (product.styleTags || []).join(', '),
        occasionTags: (product.occasionTags || []).join(', '),
      });
    }
  }, [product, isEdit]);

  // === Product submit ===
  const handleProductSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      basePrice: parseFloat(form.basePrice),
      salePrice: form.salePrice ? parseFloat(form.salePrice) : null,
      isSale: form.isSale,
      gender: form.gender,
      material: form.material.trim() || null,
      colorFamily: form.colorFamily.trim() || null,
      categoryId: parseInt(form.categoryId),
      styleTags: form.styleTags ? form.styleTags.split(',').map((t) => t.trim()).filter(Boolean) : [],
      occasionTags: form.occasionTags ? form.occasionTags.split(',').map((t) => t.trim()).filter(Boolean) : [],
    };

    try {
      if (isEdit) {
        await updateProduct.mutateAsync({ id: parseInt(productId), ...payload });
        toast.success('Cập nhật sản phẩm thành công');
      } else {
        const res = await createProduct.mutateAsync(payload);
        const newId = res?.data?.data?.id;
        toast.success('Tạo sản phẩm thành công — hãy thêm biến thể và ảnh');
        if (newId) navigate(`/admin/products/form?id=${newId}`, { replace: true });
      }
    } catch (err) {
      toast.error(err.response?.data?.message || 'Có lỗi xảy ra');
    }
  };

  // === Variant CRUD ===
  const openVariantDialog = (variant = null) => {
    setEditVariant(variant);
    setVariantForm(
      variant
        ? { color: variant.color, size: variant.size, stockQuantity: variant.stockQuantity, price: variant.priceAdjustment?.toString() || '' }
        : { color: '', size: '', stockQuantity: 0, price: '' }
    );
    setVariantDialog(true);
  };

  const handleVariantSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      productId: parseInt(productId),
      color: variantForm.color.trim(),
      size: variantForm.size.trim(),
      stockQuantity: parseInt(variantForm.stockQuantity),
      price: variantForm.price ? parseFloat(variantForm.price) : null,
    };
    try {
      if (editVariant) {
        await updateVariantMutation.mutateAsync({ ...payload, variantId: editVariant.id });
        toast.success('Cập nhật biến thể thành công');
      } else {
        await createVariant.mutateAsync(payload);
        toast.success('Thêm biến thể thành công');
      }
      setVariantDialog(false);
    } catch (err) {
      toast.error(err.response?.data?.message || 'Có lỗi xảy ra');
    }
  };

  const handleDeleteVariant = async () => {
    if (!deleteVariantTarget) return;
    try {
      await deleteVariant.mutateAsync({ productId: parseInt(productId), variantId: deleteVariantTarget.id });
      toast.success('Xóa biến thể thành công');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Không thể xóa biến thể');
    } finally {
      setDeleteVariantTarget(null);
    }
  };

  // === Image CRUD ===
  const handleUploadImage = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      await uploadImage.mutateAsync({ productId: parseInt(productId), file, isPrimary: false });
      toast.success('Tải ảnh lên thành công');
    } catch (err) {
      toast.error(err.response?.data?.message || 'Tải ảnh thất bại');
    }
    e.target.value = '';
  };

  const handleSetPrimary = async (imageId) => {
    try {
      await setPrimary.mutateAsync({ productId: parseInt(productId), imageId });
      toast.success('Đã đặt ảnh chính');
    } catch (err) {
      toast.error('Không thể đặt ảnh chính');
    }
  };

  const handleDeleteImage = async () => {
    if (!deleteImageTarget) return;
    try {
      await deleteImage.mutateAsync({ productId: parseInt(productId), imageId: deleteImageTarget });
      toast.success('Xóa ảnh thành công');
    } catch (err) {
      toast.error('Xóa ảnh thất bại');
    } finally {
      setDeleteImageTarget(null);
    }
  };

  if (isEdit && loadingProduct) {
    return <div className="flex items-center justify-center py-20"><Spinner /></div>;
  }

  const variants = product?.variants ?? [];
  const images = product?.images ?? [];
  const isSaving = createProduct.isPending || updateProduct.isPending;

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <Button variant="ghost" size="icon" onClick={() => navigate('/admin/products')}>
          <ArrowLeft className="w-5 h-5" />
        </Button>
        <h1 className="text-2xl font-bold text-gray-900">
          {isEdit ? 'Chỉnh sửa sản phẩm' : 'Thêm sản phẩm mới'}
        </h1>
      </div>

      <Tabs defaultValue="info" className="w-full">
        <TabsList className="grid w-full grid-cols-3">
          <TabsTrigger value="info">Thông tin</TabsTrigger>
          <TabsTrigger value="variants" disabled={!isEdit}>Biến thể ({variants.length})</TabsTrigger>
          <TabsTrigger value="images" disabled={!isEdit}>Hình ảnh ({images.length})</TabsTrigger>
        </TabsList>

        {/* === TAB 1: Thông tin cơ bản === */}
        <TabsContent value="info">
          <form onSubmit={handleProductSubmit} className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label>Tên sản phẩm *</Label>
                <Input required value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="Nhập tên sản phẩm" />
              </div>
              <div className="space-y-2">
                <Label>Danh mục *</Label>
                <Select value={form.categoryId} onValueChange={(val) => setForm({ ...form, categoryId: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn danh mục" /></SelectTrigger>
                  <SelectContent>
                    {categoryOptions.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Giá gốc (VNĐ) *</Label>
                <Input type="number" required min="0" value={form.basePrice}
                  onChange={(e) => setForm({ ...form, basePrice: e.target.value })} placeholder="0" />
              </div>
              <div className="space-y-2">
                <Label>Giới tính *</Label>
                <Select value={form.gender} onValueChange={(val) => setForm({ ...form, gender: val })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {GENDER_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Chất liệu</Label>
                <Input value={form.material}
                  onChange={(e) => setForm({ ...form, material: e.target.value })} placeholder="VD: Cotton, Polyester" />
              </div>
              <div className="space-y-2">
                <Label>Nhóm màu</Label>
                <Input value={form.colorFamily}
                  onChange={(e) => setForm({ ...form, colorFamily: e.target.value })} placeholder="VD: Trắng, Đen" />
              </div>
            </div>

            <Separator />

            {/* Sale */}
            <div className="space-y-4">
              <div className="flex items-center gap-3">
                <Switch checked={form.isSale}
                  onCheckedChange={(checked) => setForm({ ...form, isSale: checked })} />
                <Label>Đang khuyến mãi</Label>
              </div>
              {form.isSale && (
                <div className="space-y-2">
                  <Label>Giá khuyến mãi (VNĐ) *</Label>
                  <Input type="number" min="0" value={form.salePrice}
                    onChange={(e) => setForm({ ...form, salePrice: e.target.value })} placeholder="Phải nhỏ hơn giá gốc" />
                </div>
              )}
            </div>

            <Separator />

            {/* Tags */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2">
                <Label>Style Tags</Label>
                <Input value={form.styleTags}
                  onChange={(e) => setForm({ ...form, styleTags: e.target.value })}
                  placeholder="casual, streetwear, minimalist (cách nhau bằng dấu phẩy)" />
              </div>
              <div className="space-y-2">
                <Label>Occasion Tags</Label>
                <Input value={form.occasionTags}
                  onChange={(e) => setForm({ ...form, occasionTags: e.target.value })}
                  placeholder="dạo phố, đi làm, đi chơi" />
              </div>
            </div>

            <div className="space-y-2">
              <Label>Mô tả sản phẩm</Label>
              <Textarea rows={4} value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="Nhập mô tả chi tiết..." />
            </div>

            <div className="flex justify-end gap-3 pt-4 border-t">
              <Button type="button" variant="outline" onClick={() => navigate('/admin/products')}>Hủy</Button>
              <Button type="submit" disabled={isSaving} className="flex items-center gap-2">
                {isSaving && <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />}
                <Save className="w-4 h-4" />
                {isEdit ? 'Cập nhật' : 'Tạo sản phẩm'}
              </Button>
            </div>
          </form>
        </TabsContent>

        {/* === TAB 2: Biến thể === */}
        <TabsContent value="variants">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="p-4 border-b flex items-center justify-between">
              <h3 className="font-semibold">Biến thể (Màu × Size)</h3>
              <Button size="sm" onClick={() => openVariantDialog()} className="flex items-center gap-1">
                <Plus className="w-4 h-4" /> Thêm
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Màu</TableHead>
                  <TableHead>Size</TableHead>
                  <TableHead>Tồn kho</TableHead>
                  <TableHead>Điều chỉnh giá</TableHead>
                  <TableHead className="w-24 text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {variants.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-8 text-gray-500">
                      Chưa có biến thể. Thêm biến thể để quản lý tồn kho.
                    </TableCell>
                  </TableRow>
                ) : (
                  variants.map((v) => (
                    <TableRow key={v.id}>
                      <TableCell><Badge variant="secondary">{v.color}</Badge></TableCell>
                      <TableCell><Badge variant="outline">{v.size}</Badge></TableCell>
                      <TableCell>{v.stockQuantity}</TableCell>
                      <TableCell>{v.priceAdjustment ? `+${v.priceAdjustment.toLocaleString()}đ` : '—'}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-blue-600"
                            onClick={() => openVariantDialog(v)}>
                            <Save className="w-4 h-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-red-600"
                            onClick={() => setDeleteVariantTarget(v)}>
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
        </TabsContent>

        {/* === TAB 3: Hình ảnh === */}
        <TabsContent value="images">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold">Hình ảnh sản phẩm</h3>
              <label className="cursor-pointer">
                <input type="file" accept="image/*" className="hidden" onChange={handleUploadImage} />
                <Button type="button" size="sm" className="flex items-center gap-1" asChild>
                  <span><Upload className="w-4 h-4" /> Tải ảnh lên</span>
                </Button>
              </label>
            </div>
            {uploadImage.isPending && (
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="w-4 h-4 border-2 border-gray-400 border-t-transparent rounded-full animate-spin" />
                Đang tải ảnh lên Cloudinary...
              </div>
            )}
            {images.length === 0 ? (
              <div className="text-center py-8 text-gray-500 border-2 border-dashed border-gray-300 rounded-lg">
                Chưa có ảnh nào. Tải ảnh lên để hiển thị trên cửa hàng.
              </div>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
                {images.map((img) => (
                  <div key={img.id} className="relative group rounded-lg overflow-hidden border border-gray-200">
                    <img src={img.imageUrl} alt="" className="w-full h-40 object-cover" />
                    {img.isPrimary && (
                      <Badge className="absolute top-2 left-2 bg-yellow-500 text-white">
                        <Star className="w-3 h-3 mr-1" /> Chính
                      </Badge>
                    )}
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-2">
                      {!img.isPrimary && (
                        <Button size="sm" variant="secondary" onClick={() => handleSetPrimary(img.id)}>
                          <Star className="w-3 h-3" />
                        </Button>
                      )}
                      <Button size="sm" variant="destructive" onClick={() => setDeleteImageTarget(img.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </TabsContent>
      </Tabs>

      {/* Variant Dialog */}
      <Dialog open={variantDialog} onOpenChange={setVariantDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editVariant ? 'Sửa biến thể' : 'Thêm biến thể'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleVariantSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Màu *</Label>
                <Input required value={variantForm.color}
                  onChange={(e) => setVariantForm({ ...variantForm, color: e.target.value })}
                  placeholder="VD: Đen" />
              </div>
              <div className="space-y-2">
                <Label>Size *</Label>
                <Input required value={variantForm.size}
                  onChange={(e) => setVariantForm({ ...variantForm, size: e.target.value })}
                  placeholder="VD: M, L, XL" />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Tồn kho *</Label>
                <Input type="number" required min="0" value={variantForm.stockQuantity}
                  onChange={(e) => setVariantForm({ ...variantForm, stockQuantity: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Điều chỉnh giá</Label>
                <Input type="number" min="0" value={variantForm.price}
                  onChange={(e) => setVariantForm({ ...variantForm, price: e.target.value })}
                  placeholder="Để trống = dùng giá gốc" />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setVariantDialog(false)}>Hủy</Button>
              <Button type="submit" disabled={createVariant.isPending || updateVariantMutation.isPending}>
                {editVariant ? 'Cập nhật' : 'Thêm'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Variant Dialog */}
      <AlertDialog open={!!deleteVariantTarget} onOpenChange={(open) => !open && setDeleteVariantTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa biến thể "{deleteVariantTarget?.color} - {deleteVariantTarget?.size}"?</AlertDialogTitle>
            <AlertDialogDescription>Hành động này không thể hoàn tác.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteVariant} className="bg-red-600 hover:bg-red-700">Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Delete Image Dialog */}
      <AlertDialog open={!!deleteImageTarget} onOpenChange={(open) => !open && setDeleteImageTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa ảnh này?</AlertDialogTitle>
            <AlertDialogDescription>Ảnh sẽ bị xóa khỏi Cloudinary và không thể khôi phục.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteImage} className="bg-red-600 hover:bg-red-700">Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default ProductFormPage;
