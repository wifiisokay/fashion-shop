import { useState, useEffect } from 'react';
import dayjs from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Save, Plus, Trash2, Upload } from 'lucide-react';
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
import { useUploadColorThumbnail, useUploadGalleryImage, useReorderImage, useDeleteImage } from '@/hooks/useAdminImages';
import { useCreateColor, useUpdateColor, useDeleteColor } from '@/hooks/useAdminColors';
import { useTagLibrary } from '@/hooks/useTagLibrary';
import { useTagSuggestion } from '@/hooks/useTagSuggestion';
import Spinner from '@/components/ui/Spinner';
import TagPanel from '@/components/product/TagPanel';
import { DragDropContext, Droppable, Draggable } from '@hello-pangea/dnd';

const GENDER_OPTIONS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
];

const SEASON_LABELS = {
  'ALL_SEASON': '4 mùa',
  'SPRING_SUMMER': 'Xuân/Hè',
  'FALL_WINTER': 'Thu/Đông'
};

const normalizeSeason = (val) => {
  if (!val) return 'ALL_SEASON';
  const clean = val.toString().trim().toUpperCase();
  if (clean === 'ALL_SEASON' || clean === '4 MÙA' || clean === '4MUA' || clean === 'MÙA') return 'ALL_SEASON';
  if (clean === 'SPRING_SUMMER' || clean === 'XUÂN/HÈ' || clean === 'XUAN/HE' || clean === 'XUÂN HÈ') return 'SPRING_SUMMER';
  if (clean === 'FALL_WINTER' || clean === 'THU/ĐÔNG' || clean === 'THU/DONG' || clean === 'THU ĐÔNG') return 'FALL_WINTER';
  return 'ALL_SEASON';
};

const COLOR_FAMILY_LABELS = {
  neutral: 'Trung tinh',
  cool: 'Tong lanh',
  warm: 'Tong am',
  earth: 'Tong dat',
  mixed: 'Phoi mau',
};

const ProductFormPage = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const productId = searchParams.get('id');
  const isEdit = !!productId;

  // Data fetching
  const { data: product, isLoading: loadingProduct } = useAdminProduct(productId);
  const { data: categories = [] } = useCategories();
  const { data: tagLibrary } = useTagLibrary();
  const suggestTagsMutation = useTagSuggestion();
  const createProduct = useCreateProduct();
  const updateProduct = useUpdateProduct();
  const createVariant = useCreateVariant();
  const updateVariantMutation = useUpdateVariant();
  const deleteVariant = useDeleteVariant();
  const uploadColorThumbnail = useUploadColorThumbnail();
  const uploadGalleryImage = useUploadGalleryImage();
  const reorderImage = useReorderImage();
  const deleteImage = useDeleteImage();
  const createColor = useCreateColor();
  const updateColorMutation = useUpdateColor();
  const deleteColorMutation = useDeleteColor();

  // Form state
  const [form, setForm] = useState({
    name: '', description: '', basePrice: '', salePrice: '',
    isSale: false, gender: 'MALE', material: '', estimatedWeight: '300', colorFamily: '',
    categoryId: '', styleTags: [], occasionTags: [],
    fitType: '', season: 'ALL_SEASON',
    saleStartAt: '', saleEndAt: '', lowStockThreshold: '10',
    aiSuggestedStyleTags: [], aiSuggestedOccasionTags: []
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
        styleTags: product.styleTags || [],
        occasionTags: product.occasionTags || [],
        fitType: product.fitType || '',
        season: normalizeSeason(product.season),
        saleStartAt: product.saleStartAt ? dayjs(product.saleStartAt).format('YYYY-MM-DDTHH:mm') : '',
        saleEndAt: product.saleEndAt ? dayjs(product.saleEndAt).format('YYYY-MM-DDTHH:mm') : '',
        lowStockThreshold: product.lowStockThreshold?.toString() || '10',
        aiSuggestedStyleTags: [],
        aiSuggestedOccasionTags: []
      });
    }
  }, [product, isEdit]);

  const variants = product?.variants ?? [];
  const colors = product?.colors ?? [];
  const images = (product?.images ?? [])
    .filter((img) => !img.colorId && !img.isPrimary)
    .slice()
    .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0));
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
      categoryId: parseInt(form.categoryId),
      styleTags: form.styleTags,
      occasionTags: form.occasionTags,
      fitType: form.fitType || null,
      season: form.season || null,
      saleStartAt: form.saleStartAt || null,
      saleEndAt: form.saleEndAt || null,
      lowStockThreshold: form.lowStockThreshold ? parseInt(form.lowStockThreshold) : 10,
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

  const handleTagToggle = (field, tag) => {
    setForm((prev) => {
      const currentTags = prev[field];
      if (currentTags.includes(tag)) {
        return { ...prev, [field]: currentTags.filter((t) => t !== tag) };
      }
      if (currentTags.length >= 4) return prev;
      return { ...prev, [field]: [...currentTags, tag] };
    });
  };

  const handleAISuggest = () => {
    if (!form.name || form.description?.length < 20) {
      toast.warning('Nhập tên và mô tả ít nhất 20 ký tự để AI gợi ý');
      return;
    }

    const selectedCategory = categoryOptions.find(c => c.value === form.categoryId);

    suggestTagsMutation.mutate({
      name: form.name,
      description: form.description,
      gender: form.gender,
      categoryName: selectedCategory?.label?.replace('  └ ', ''),
      material: form.material,
    }, {
      onSuccess: (data) => {
        setForm(prev => {
          const mergeUnique = (arr1, arr2, max) => {
            const set = new Set([...arr1, ...arr2]);
            return Array.from(set).slice(0, max);
          };

          return {
            ...prev,
            styleTags: mergeUnique(prev.styleTags, data.styleTags || [], 4),
            occasionTags: mergeUnique(prev.occasionTags, data.occasionTags || [], 4),
            fitType: data.fitType || prev.fitType,
            aiSuggestedStyleTags: data.styleTags || [],
            aiSuggestedOccasionTags: data.occasionTags || [],
          };
        });
        toast.success(`Gợi ý thành công (Độ tin cậy: ${data.confidence})`);
      }
    });
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
    const payload = {
      productId: parseInt(productId),
      colorName: colorForm.colorName.trim(),
      colorCode: colorForm.colorCode ? colorForm.colorCode.trim() : null,
      displayOrder: colorForm.displayOrder
    };
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
  const handleUploadColorThumbnail = async (e, colorId) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      await uploadColorThumbnail.mutateAsync({ productId: parseInt(productId), colorId, file });
      toast.success('Tải ảnh thẻ lên thành công');
    } catch {
      toast.error('Tải ảnh thẻ thất bại');
    }
    e.target.value = '';
  };

  const handleUploadGalleryImages = async (e) => {
    const files = Array.from(e.target.files || []);
    if (files.length === 0) return;

    let successCount = 0;
    for (const file of files) {
      try {
        await uploadGalleryImage.mutateAsync({ productId: parseInt(productId), file });
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

    if (source.droppableId !== 'shared-gallery') return;

    // Current sorted images
    const colorImages = images.slice();
    
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
                <Select key={form.categoryId} value={form.categoryId} onValueChange={(val) => setForm({ ...form, categoryId: val })}>
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
                <Select key={form.gender} value={form.gender} onValueChange={(val) => setForm({ ...form, gender: val })}>
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
                <Label>Ngưỡng cảnh báo tồn kho *</Label>
                <Input type="number" required min="0" value={form.lowStockThreshold}
                  onChange={(e) => setForm({ ...form, lowStockThreshold: e.target.value })}
                  placeholder="10" />
                <p className="text-xs text-gray-400">Cảnh báo khi tồn kho biến thể ít hơn hoặc bằng số này.</p>
              </div>
              <div className="space-y-2">
                <Label>Nhóm màu</Label>
                <div className="rounded-md border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-700">
                  {COLOR_FAMILY_LABELS[form.colorFamily] || form.colorFamily || 'Tu dong theo mau chinh'}
                </div>
                <p className="text-xs text-gray-400">Backend tu derive tu ma mau va sync theo mau co thu tu nho nhat.</p>
              </div>
            </div>

            <Separator />

            {/* Sale */}
            <div className="space-y-4 border p-4 rounded-xl bg-gray-50/50">
              <div className="flex items-center gap-3">
                <Switch checked={form.isSale}
                  onCheckedChange={(checked) => setForm({ ...form, isSale: checked })} />
                <Label className="font-bold text-gray-900">Áp dụng chương trình giảm giá</Label>
              </div>
              
              {form.isSale && (
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                  <div className="space-y-2">
                    <Label>Giá khuyến mãi (VNĐ) *</Label>
                    <Input type="number" min="0" value={form.salePrice}
                      onChange={(e) => setForm({ ...form, salePrice: e.target.value })} placeholder="Phải nhỏ hơn giá gốc" />
                  </div>
                  <div className="space-y-2">
                    <Label>Thời gian bắt đầu</Label>
                    <Input type="datetime-local" value={form.saleStartAt}
                      onChange={(e) => setForm({ ...form, saleStartAt: e.target.value })} />
                  </div>
                  <div className="space-y-2">
                    <Label>Thời gian kết thúc</Label>
                    <Input type="datetime-local" value={form.saleEndAt}
                      onChange={(e) => setForm({ ...form, saleEndAt: e.target.value })} />
                  </div>
                  
                  {form.basePrice && form.salePrice && parseFloat(form.basePrice) > 0 && (
                    <div className="md:col-span-3 text-xs font-bold text-emerald-700 bg-emerald-50 px-3 py-2.5 rounded-lg border border-emerald-200/50 flex items-center justify-between">
                      <span>Tỷ lệ giảm giá dự kiến:</span>
                      <span className="font-black text-sm text-emerald-800">
                        -{Math.round((1 - parseFloat(form.salePrice) / parseFloat(form.basePrice)) * 100)}%
                      </span>
                    </div>
                  )}
                </div>
              )}
            </div>

            <Separator />

            {/* Tags */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div className="space-y-2 md:col-span-2 grid grid-cols-1 md:grid-cols-2 gap-6">
                <TagPanel
                  label="Style Tags"
                  tags={tagLibrary?.styleTags || []}
                  selectedTags={form.styleTags}
                  aiSuggestedTags={form.aiSuggestedStyleTags}
                  onToggle={(tag) => handleTagToggle('styleTags', tag)}
                  onSuggestClick={handleAISuggest}
                  isLoadingSuggest={suggestTagsMutation.isPending}
                  showSuggestBtn={true}
                  maxCount={4}
                />
                <TagPanel
                  label="Occasion Tags"
                  tags={tagLibrary?.occasionTags || []}
                  selectedTags={form.occasionTags}
                  aiSuggestedTags={form.aiSuggestedOccasionTags}
                  onToggle={(tag) => handleTagToggle('occasionTags', tag)}
                  maxCount={4}
                />
              </div>

              <div className="space-y-2">
                <Label>Kiểu dáng (Fit Type)</Label>
                <Select key={form.fitType} value={form.fitType} onValueChange={(val) => setForm({ ...form, fitType: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn kiểu dáng" /></SelectTrigger>
                  <SelectContent>
                    {(tagLibrary?.fitTypes || []).map((f) => <SelectItem key={f} value={f}>{f}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Mùa (Season)</Label>
                <Select key={form.season} value={form.season} onValueChange={(val) => setForm({ ...form, season: val })}>
                  <SelectTrigger><SelectValue placeholder="Chọn mùa" /></SelectTrigger>
                  <SelectContent>
                    {Object.keys(SEASON_LABELS).map((s) => (
                      <SelectItem key={s} value={s}>{SEASON_LABELS[s]}</SelectItem>
                    ))}
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
                  <TableHead>Nhóm màu</TableHead>
                  <TableHead>Thứ tự</TableHead>
                  <TableHead className="w-24 text-right">Hành động</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {colors.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center py-8 text-gray-500">
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
                      <TableCell>
                        <Badge variant="outline">{COLOR_FAMILY_LABELS[c.colorFamily] || c.colorFamily || 'Chua co'}</Badge>
                      </TableCell>
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

        {/* === TAB 4: Hinh anh === */}
        <TabsContent value="images">
          <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-6">
            {(uploadColorThumbnail.isPending || uploadGalleryImage.isPending || reorderImage.isPending) && (
              <div className="flex items-center gap-2 text-sm text-gray-500">
                <span className="w-4 h-4 border-2 border-gray-400 border-t-transparent rounded-full animate-spin" />
                Dang xu ly anh...
              </div>
            )}

            <div className="space-y-3">
              <h4 className="font-semibold">Thumbnail theo mau</h4>
              {colors.length === 0 ? (
                <div className="text-center py-6 text-gray-400 border-2 border-dashed rounded-lg text-sm">
                  Hay tao mau truoc khi upload thumbnail.
                </div>
              ) : (
                <div className="grid sm:grid-cols-2 gap-3">
                  {colors.map((c) => (
                    <div key={c.id} className="flex items-center gap-3 rounded-lg border border-gray-200 p-3">
                      <div className="w-20 h-24 rounded-md overflow-hidden bg-gray-100 border shrink-0">
                        {c.thumbnailUrl ? (
                          <img src={c.thumbnailUrl} alt={c.colorName} className="w-full h-full object-cover" />
                        ) : (
                          <div className="w-full h-full flex items-center justify-center text-xs text-gray-400 text-center px-2">
                            Chua co anh
                          </div>
                        )}
                      </div>
                      <div className="min-w-0 flex-1 space-y-2">
                        <div className="flex items-center gap-2 text-sm font-medium">
                          <span className="w-4 h-4 rounded-full border" style={{ backgroundColor: c.colorCode || '#ccc' }} />
                          <span className="truncate">{c.colorName}</span>
                        </div>
                        <div className="flex items-center gap-2">
                          <label className="cursor-pointer">
                            <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden"
                              onChange={(e) => handleUploadColorThumbnail(e, c.id)} />
                            <Button type="button" size="sm" variant="outline" className="flex items-center gap-1" asChild>
                              <span><Upload className="w-4 h-4" /> Upload</span>
                            </Button>
                          </label>
                          {c.thumbnailUrl && (
                            <Button type="button" size="sm" variant="destructive"
                              onClick={() => c.thumbnailImageId && setDeleteImageTarget(c.thumbnailImageId)}>
                              <Trash2 className="w-3 h-3" />
                            </Button>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <Separator />

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <h4 className="font-semibold">Gallery chung</h4>
                <label className="cursor-pointer">
                  <input type="file" accept="image/jpeg,image/png,image/webp" className="hidden" multiple
                    onChange={handleUploadGalleryImages} />
                  <Button type="button" size="sm" className="flex items-center gap-1" asChild>
                    <span><Upload className="w-4 h-4" /> Upload gallery</span>
                  </Button>
                </label>
              </div>

              <DragDropContext onDragEnd={handleDragEnd}>
                <Droppable droppableId="shared-gallery" direction="horizontal">
                  {(provided) => (
                    <div
                      ref={provided.innerRef}
                      {...provided.droppableProps}
                      className={`flex flex-wrap gap-2 min-h-[120px] p-2 rounded-lg border-2 border-dashed ${images.length === 0 ? 'bg-gray-50 items-center justify-center' : 'bg-white'}`}
                    >
                      {images.length > 0 ? (
                        images.map((img, index) => (
                          <Draggable key={img.id} draggableId={img.id.toString()} index={index}>
                            {(provided, snapshot) => (
                              <div
                                ref={provided.innerRef}
                                {...provided.draggableProps}
                                {...provided.dragHandleProps}
                                className={`relative group rounded-lg overflow-hidden border w-[110px] h-[110px] shrink-0 ${snapshot.isDragging ? 'shadow-xl ring-2 ring-primary z-50' : ''}`}
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
                        <p className="text-xs text-gray-400 italic">Chua co anh gallery</p>
                      )}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>
              </DragDropContext>
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
