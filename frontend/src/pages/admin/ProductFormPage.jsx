import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Save, Plus, Trash2, Upload, Star } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import MarkdownEditor from '@/components/ui/MarkdownEditor';
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
import { useUploadPrimaryImage, useUploadColorImage, useReorderImage, useDeleteImage } from '@/hooks/useAdminImages';
import { useCreateColor, useUpdateColor, useDeleteColor } from '@/hooks/useAdminColors';
import Spinner from '@/components/ui/Spinner';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'UNISEX', label: 'Unisex' },
];
const FIT_OPTIONS = ['Slim', 'Regular', 'Oversized', 'Relaxed'];
const SEASON_OPTIONS = ['Hè', 'Đông', '4 mùa'];

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
  const uploadPrimaryImage = useUploadPrimaryImage();
  const uploadColorImage = useUploadColorImage();
  const reorderImage = useReorderImage();
  const deleteImage = useDeleteImage();
  const createColor = useCreateColor();
  const updateColorMutation = useUpdateColor();
  const deleteColorMutation = useDeleteColor();

  // Form state
  const [form, setForm] = useState({
    name: '', description: '', basePrice: '', salePrice: '',
    isSale: false, gender: 'MALE', material: '', estimatedWeight: '300', colorFamily: '',
    categoryId: '', styleTags: '', occasionTags: '',
    fitType: '', season: '',
  });

  // Variant dialog state
  const [variantDialog, setVariantDialog] = useState(false);
  const [editVariant, setEditVariant] = useState(null);
  const [variantForm, setVariantForm] = useState({ colorId: '', size: '', stockQuantity: 0, price: '' });
  const [deleteVariantTarget, setDeleteVariantTarget] = useState(null);
  const [deleteImageTarget, setDeleteImageTarget] = useState(null);

  // Color dialog state
  const [colorDialog, setColorDialog] = useState(false);
  const [editColor, setEditColor] = useState(null);
  const [colorForm, setColorForm] = useState({ colorName: '', colorCode: '#000000', displayOrder: 0 });
  const [deleteColorTarget, setDeleteColorTarget] = useState(null);

  // Image upload state
  const [uploadColorId, setUploadColorId] = useState('');

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
        estimatedWeight: product.estimatedWeight?.toString() || '300',
        colorFamily: product.colorFamily || '',
        categoryId: product.category?.id?.toString() || '',
        styleTags: (product.styleTags || []).join(', '),
        occasionTags: (product.occasionTags || []).join(', '),
        fitType: product.fitType || '',
        season: product.season || '',
      });
    }
  }, [product, isEdit]);

  const variants = product?.variants ?? [];
  const colors = product?.colors ?? [];
  const images = product?.images ?? [];
  const isSaving = createProduct.isPending || updateProduct.isPending;

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
      estimatedWeight: form.estimatedWeight ? parseInt(form.estimatedWeight) : 300,
      colorFamily: form.colorFamily.trim() || null,
      categoryId: parseInt(form.categoryId),
      styleTags: form.styleTags ? form.styleTags.split(',').map((t) => t.trim()).filter(Boolean) : [],
      occasionTags: form.occasionTags ? form.occasionTags.split(',').map((t) => t.trim()).filter(Boolean) : [],
      fitType: form.fitType || null,
      season: form.season || null,
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
        ? { colorId: variant.colorId?.toString() || '', size: variant.size, stockQuantity: variant.stockQuantity, price: variant.priceAdjustment?.toString() || '' }
        : { colorId: '', size: '', stockQuantity: 0, price: form.basePrice || '' }
    );
    setVariantDialog(true);
  };

  const handleVariantSubmit = async (e) => {
    e.preventDefault();
    const payload = {
      productId: parseInt(productId),
      colorId: parseInt(variantForm.colorId),
      size: variantForm.size.trim(),
      stockQuantity: parseInt(variantForm.stockQuantity),
      price: variantForm.price ? parseFloat(variantForm.price) : (form.basePrice ? parseFloat(form.basePrice) : null),
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
    } catch {
      toast.error('Có lỗi xảy ra');
    }
  };

  const handleDeleteVariant = async () => {
    if (!deleteVariantTarget) return;
    try {
      await deleteVariant.mutateAsync({ productId: parseInt(productId), variantId: deleteVariantTarget.id });
      toast.success('Xóa biến thể thành công');
    } catch {
      toast.error('Không thể xóa biến thể');
    } finally {
      setDeleteVariantTarget(null);
    }
  };

  // === Color CRUD ===
  const openColorDialog = (color = null) => {
    setEditColor(color);
    setColorForm(
      color
        ? { colorName: color.colorName, colorCode: color.colorCode || '#000000', displayOrder: color.displayOrder || 0 }
        : { colorName: '', colorCode: '#000000', displayOrder: 0 }
    );
    setColorDialog(true);
  };

  const handleColorSubmit = async (e) => {
    e.preventDefault();
    const payload = { productId: parseInt(productId), ...colorForm };
    try {
      if (editColor) {
        await updateColorMutation.mutateAsync({ ...payload, colorId: editColor.id });
        toast.success('Cập nhật màu thành công');
      } else {
        await createColor.mutateAsync(payload);
        toast.success('Thêm màu thành công');
      }
      setColorDialog(false);
    } catch {
      toast.error('Có lỗi xảy ra');
    }
  };

  const handleDeleteColor = async () => {
    if (!deleteColorTarget) return;
    try {
      await deleteColorMutation.mutateAsync({ productId: parseInt(productId), colorId: deleteColorTarget.id });
      toast.success('Xóa màu thành công');
    } catch {
      toast.error('Không thể xóa màu');
    } finally {
      setDeleteColorTarget(null);
    }
  };

  // === Image CRUD ===
  const handleUploadPrimaryImage = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      await uploadPrimaryImage.mutateAsync({ productId: parseInt(productId), file });
      toast.success('Tải ảnh thẻ lên thành công');
    } catch {
      toast.error('Tải ảnh thẻ thất bại');
    }
    e.target.value = '';
  };

  const handleUploadColorImages = async (e, colorId) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    const targetColor = colors.find((c) => c.id === colorId);
    const currentCount = targetColor?.images?.length || 0;
    
    if (currentCount + files.length > 5) {
      toast.error(`Chỉ được tải lên tối đa 5 ảnh cho mỗi màu sắc. Màu này đã có ${currentCount} ảnh.`);
      e.target.value = '';
      return;
    }

    let successCount = 0;
    for (const file of files) {
      try {
        await uploadColorImage.mutateAsync({ productId: parseInt(productId), colorId, file });
        successCount++;
      } catch {
        toast.error(`Tải ảnh ${file.name} thất bại`);
      }
    }
    
    if (successCount > 0) {
      toast.success(`Tải thành công ${successCount}/${files.length} ảnh`);
    }
    e.target.value = '';
  };

  const handleDragEnd = async (result) => {
    if (!result.destination) return;
    const { source, destination } = result;

    if (source.droppableId !== destination.droppableId || source.index === destination.index) {
      return;
    }

    const colorIdStr = source.droppableId.replace('color-', '');
    const colorId = parseInt(colorIdStr);
    const targetColor = colors.find((c) => c.id === colorId);
    if (!targetColor) return;

    // Current sorted images
    const colorImages = (targetColor.images || []).slice().sort((a, b) => a.sortOrder - b.sortOrder);
    
    // Reorder locally
    const [movedImage] = colorImages.splice(source.index, 1);
    colorImages.splice(destination.index, 0, movedImage);

    try {
      const promises = colorImages.map((img, index) => {
        // Only update if sortOrder actually changed
        if (img.sortOrder !== index) {
          return reorderImage.mutateAsync({
            productId: parseInt(productId),
            imageId: img.id,
            newSortOrder: index
          });
        }
        return Promise.resolve();
      });
      
      await Promise.all(promises);
      toast.success('Đã cập nhật thứ tự ảnh');
    } catch {
      toast.error('Cập nhật thứ tự thất bại');
    }
  };

  const handleDeleteImage = async () => {
    if (!deleteImageTarget) return;
    try {
      await deleteImage.mutateAsync({ productId: parseInt(productId), imageId: deleteImageTarget });
      toast.success('Xóa ảnh thành công');
    } catch {
      toast.error('Xóa ảnh thất bại');
    } finally {
      setDeleteImageTarget(null);
    }
  };

  if (isEdit && loadingProduct) {
    return <div className="flex items-center justify-center py-20"><Spinner /></div>;
  }

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
        <TabsList className="grid w-full grid-cols-5">
          <TabsTrigger value="info">Thông tin</TabsTrigger>
          <TabsTrigger value="description">Mô tả</TabsTrigger>
          <TabsTrigger value="colors" disabled={!isEdit}>Màu sắc ({colors.length})</TabsTrigger>
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
                <Label>Cân nặng ước tính (gram)</Label>
                <Input type="number" value={form.estimatedWeight}
                  onChange={(e) => setForm({ ...form, estimatedWeight: e.target.value })}
                  placeholder="300" min="50" max="50000" />
                <p className="text-xs text-gray-400">Dùng tính phí ship. Mặc định 300g cho quần áo.</p>
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
                  placeholder="casual, streetwear, minimalist" />
              </div>
              <div className="space-y-2">
                <Label>Occasion Tags</Label>
                <Input value={form.occasionTags}
                  onChange={(e) => setForm({ ...form, occasionTags: e.target.value })}
                  placeholder="dạo phố, đi làm, đi chơi" />
              </div>
              <div className="space-y-2">
                <Label>Kiểu dáng (Fit Type)</Label>
                <Select value={form.fitType} onValueChange={(val) => setForm({ ...form, fitType: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn kiểu dáng" /></SelectTrigger>
                  <SelectContent>
                    {FIT_OPTIONS.map((f) => <SelectItem key={f} value={f}>{f}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Mùa (Season)</Label>
                <Select value={form.season} onValueChange={(val) => setForm({ ...form, season: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn mùa" /></SelectTrigger>
                  <SelectContent>
                    {SEASON_OPTIONS.map((s) => <SelectItem key={s} value={s}>{s}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
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

        {/* === TAB 5: Mô tả === */}
        <TabsContent value="description">
          <form onSubmit={handleProductSubmit} className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold text-lg">Mô tả chi tiết sản phẩm</h3>
              <p className="text-sm text-gray-500">Hỗ trợ viết theo định dạng Markdown.</p>
            </div>
            
            <MarkdownEditor
              value={form.description}
              onChange={(val) => setForm({ ...form, description: val })}
              placeholder="Nhập mô tả chi tiết... (hỗ trợ Markdown)"
            />

            <div className="flex justify-end gap-3 pt-4 border-t">
              <Button type="submit" disabled={isSaving} className="flex items-center gap-2">
                {isSaving && <span className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />}
                <Save className="w-4 h-4" />
                {isEdit ? 'Cập nhật mô tả' : 'Lưu mô tả'}
              </Button>
            </div>
          </form>
        </TabsContent>

        {/* === TAB 2: Màu sắc === */}
        <TabsContent value="colors">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
            <div className="p-4 border-b flex items-center justify-between">
              <h3 className="font-semibold">Màu sắc sản phẩm</h3>
              <Button size="sm" onClick={() => openColorDialog()} className="flex items-center gap-1">
                <Plus className="w-4 h-4" /> Thêm màu
              </Button>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tên màu</TableHead>
                  <TableHead>Mã màu</TableHead>
                  <TableHead>Thứ tự</TableHead>
                  <TableHead className="w-24 text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {colors.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} className="text-center py-8 text-gray-500">
                      Chưa có màu nào. Thêm màu để tạo biến thể.
                    </TableCell>
                  </TableRow>
                ) : (
                  colors.map((c) => (
                    <TableRow key={c.id}>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <span className="w-5 h-5 rounded-full border border-gray-300" style={{ backgroundColor: c.colorCode || '#ccc' }} />
                          {c.colorName}
                        </div>
                      </TableCell>
                      <TableCell><code className="text-xs">{c.colorCode}</code></TableCell>
                      <TableCell>{c.displayOrder}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-blue-600" onClick={() => openColorDialog(c)}>
                            <Save className="w-4 h-4" />
                          </Button>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-red-600" onClick={() => setDeleteColorTarget(c)}>
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
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <span className="w-4 h-4 rounded-full border" style={{ backgroundColor: colors.find(c => c.id === v.colorId)?.colorCode || '#ccc' }} />
                        <Badge variant="secondary">{v.colorName || 'N/A'}</Badge>
                      </div>
                    </TableCell>
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

        {/* === TAB 4: Hình ảnh === */}
        <TabsContent value="images">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
            {(uploadPrimaryImage.isPending || uploadColorImage.isPending || reorderImage.isPending) && (
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="w-4 h-4 border-2 border-gray-400 border-t-transparent rounded-full animate-spin" />
                Đang xử lý ảnh...
              </div>
            )}

            {/* Ảnh chính (color=null, isPrimary=true) */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold">Ảnh chính (Listing)</h4>
                <label className="cursor-pointer">
                  <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
                    onChange={handleUploadPrimaryImage} />
                  <Button type="button" size="sm" variant="outline" className="flex items-center gap-1" asChild>
                    <span><Upload className="w-4 h-4" /> Upload ảnh chính</span>
                  </Button>
                </label>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {images.filter(i => !i.colorId && i.isPrimary).map((img) => (
                  <div key={img.id} className="relative group rounded-lg overflow-hidden border-2 border-yellow-400">
                    <img src={img.imageUrl} alt="" className="w-full h-36 object-cover" />
                    <Badge className="absolute top-2 left-2 bg-yellow-500 text-white text-xs">
                      <Star className="w-3 h-3 mr-1" /> Chính
                    </Badge>
                    <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                      <Button size="sm" variant="destructive" onClick={() => setDeleteImageTarget(img.id)}>
                        <Trash2 className="w-3 h-3" />
                      </Button>
                    </div>
                  </div>
                ))}
                {images.filter(i => !i.colorId && i.isPrimary).length === 0 && (
                  <div className="col-span-full text-center py-6 text-gray-400 border-2 border-dashed rounded-lg text-sm">
                    Chưa có ảnh chính. Upload để hiển thị trên listing.
                  </div>
                )}
              </div>
            </div>

            <Separator />

            {/* Ảnh theo màu */}
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold">Ảnh theo màu</h4>
                <div className="flex items-center gap-2">
                  <Select value={uploadColorId} onValueChange={setUploadColorId}>
                    <SelectTrigger className="w-40"><SelectValue placeholder="Chọn màu" /></SelectTrigger>
                    <SelectContent>
                      {colors.map((c) => <SelectItem key={c.id} value={c.id.toString()}>{c.colorName}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <label className={`cursor-pointer ${!uploadColorId ? 'pointer-events-none opacity-50' : ''}`}>
                    <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" multiple
                      onChange={(e) => handleUploadColorImages(e, parseInt(uploadColorId))} disabled={!uploadColorId} />
                    <Button type="button" size="sm" className="flex items-center gap-1" asChild disabled={!uploadColorId}>
                      <span><Upload className="w-4 h-4" /> Upload</span>
                    </Button>
                  </label>
                </div>
              </div>
              
              {colors.length === 0 ? (
                <div className="text-center py-6 text-gray-400 border-2 border-dashed rounded-lg text-sm">
                  Hãy tạo màu sắc trước để upload ảnh theo màu.
                </div>
              ) : (
                <DragDropContext onDragEnd={handleDragEnd}>
                  {colors.map((c) => {
                    const colorImages = (c.images || [])
                      .sort((a, b) => a.sortOrder - b.sortOrder);

                    return (
                      <div key={c.id} className="space-y-2">
                        <div className="flex items-center gap-2 text-sm font-medium">
                          <span className="w-4 h-4 rounded-full border" style={{ backgroundColor: c.colorCode || '#ccc' }} />
                          {c.colorName} ({colorImages.length}/5 ảnh)
                        </div>
                        
                        <Droppable droppableId={`color-${c.id}`} direction="horizontal">
                          {(provided) => (
                            <div 
                              ref={provided.innerRef}
                              {...provided.droppableProps}
                              className={`flex flex-wrap gap-2 min-h-[120px] p-2 rounded-lg border-2 border-dashed ${colorImages.length === 0 ? 'bg-gray-50 items-center justify-center' : 'bg-white'}`}
                            >
                              {colorImages.length > 0 ? (
                                colorImages.map((img, index) => (
                                  <Draggable key={img.id} draggableId={img.id.toString()} index={index}>
                                    {(provided, snapshot) => (
                                      <div
                                        ref={provided.innerRef}
                                        {...provided.draggableProps}
                                        {...provided.dragHandleProps}
                                        className={`relative group rounded-lg overflow-hidden border w-[110px] h-[110px] flex-shrink-0 ${snapshot.isDragging ? 'shadow-xl ring-2 ring-primary z-50' : ''}`}
                                      >
                                        <img src={img.imageUrl} alt="" className="w-full h-full object-cover" />
                                        <div className="absolute top-1 left-1 bg-black/50 text-white text-[10px] px-1.5 py-0.5 rounded backdrop-blur-sm">
                                          {index + 1}
                                        </div>
                                        <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                          <Button size="sm" variant="destructive" onClick={() => setDeleteImageTarget(img.id)}>
                                            <Trash2 className="w-3 h-3" />
                                          </Button>
                                        </div>
                                      </div>
                                    )}
                                  </Draggable>
                                ))
                              ) : (
                                <p className="text-xs text-gray-400 italic">Chưa có ảnh</p>
                              )}
                              {provided.placeholder}
                            </div>
                          )}
                        </Droppable>
                      </div>
                    );
                  })}
                </DragDropContext>
              )}
            </div>
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
                {colors.length === 0 ? (
                  <p className="text-sm text-amber-600">Hãy tạo màu trước ở tab Màu sắc</p>
                ) : (
                  <Select value={variantForm.colorId} onValueChange={(val) => setVariantForm({ ...variantForm, colorId: val })}>
                    <SelectTrigger><SelectValue placeholder="Chọn màu" /></SelectTrigger>
                    <SelectContent>
                      {colors.map((c) => (
                        <SelectItem key={c.id} value={c.id.toString()}>
                          <div className="flex items-center gap-2">
                            <span className="w-3 h-3 rounded-full border" style={{ backgroundColor: c.colorCode || '#ccc' }} />
                            {c.colorName}
                          </div>
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
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
            <AlertDialogTitle>Xóa biến thể "{deleteVariantTarget?.colorName || 'N/A'} - {deleteVariantTarget?.size}"?</AlertDialogTitle>
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

      {/* Color Dialog */}
      <Dialog open={colorDialog} onOpenChange={setColorDialog}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editColor ? 'Sửa màu' : 'Thêm màu'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleColorSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label>Tên màu *</Label>
              <Input required value={colorForm.colorName}
                onChange={(e) => setColorForm({ ...colorForm, colorName: e.target.value })}
                placeholder="VD: Đen, Trắng, Xanh navy" />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Mã màu</Label>
                <div className="flex items-center gap-2">
                  <input type="color" value={colorForm.colorCode}
                    onChange={(e) => setColorForm({ ...colorForm, colorCode: e.target.value })}
                    className="w-10 h-10 rounded border cursor-pointer" />
                  <Input value={colorForm.colorCode}
                    onChange={(e) => setColorForm({ ...colorForm, colorCode: e.target.value })}
                    placeholder="#000000" className="flex-1" />
                </div>
              </div>
              <div className="space-y-2">
                <Label>Thứ tự hiển thị</Label>
                <Input type="number" min="0" value={colorForm.displayOrder}
                  onChange={(e) => setColorForm({ ...colorForm, displayOrder: parseInt(e.target.value) || 0 })} />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setColorDialog(false)}>Hủy</Button>
              <Button type="submit" disabled={createColor.isPending || updateColorMutation.isPending}>
                {editColor ? 'Cập nhật' : 'Thêm'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Color Dialog */}
      <AlertDialog open={!!deleteColorTarget} onOpenChange={(open) => !open && setDeleteColorTarget(null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Xóa màu "{deleteColorTarget?.colorName}"?</AlertDialogTitle>
            <AlertDialogDescription>
              Sẽ xóa cả biến thể và ảnh liên quan đến màu này. Hành động không thể hoàn tác.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Hủy</AlertDialogCancel>
            <AlertDialogAction onClick={handleDeleteColor} className="bg-red-600 hover:bg-red-700">Xóa</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};

export default ProductFormPage;
