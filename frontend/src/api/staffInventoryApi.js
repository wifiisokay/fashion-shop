import { productApi } from './productApi';

export const staffInventoryApi = {
  getInventory: async (params = {}) => {
    const { keyword, categoryId, gender, status, sortBy, sortDir, page = 0, size = 10 } = params;

    // Fetch matching products from the admin list API.
    // Use a large size to retrieve products, then flatten and filter at the variant level.
    const { data: listData } = await productApi.adminList({
      keyword,
      categoryId,
      gender,
      page: 0,
      size: 1000
    });

    const products = listData?.data?.content || [];

    // Fetch full details of each product in parallel to access detailed variants and color schemes.
    const detailsPromises = products.map((p) => productApi.adminGetById(p.id));
    const detailsResponses = await Promise.all(detailsPromises);
    const productsDetails = detailsResponses.map((r) => r.data?.data).filter(Boolean);

    // Flatten products to variants
    const allVariants = [];
    productsDetails.forEach((prod) => {
      const categoryName = prod.category?.name || prod.categoryName || 'Không có';
      const lowStockThreshold = prod.lowStockThreshold;

      (prod.variants || []).forEach((v) => {
        const colorInfo = (prod.colors || []).find((c) => c.id === v.colorId);
        const imageUrl = colorInfo?.thumbnailUrl || prod.primaryImageUrl;
        const qty = v.stockQuantity ?? 0;

        // Compute stock status
        let computedStatus = 'IN_STOCK';
        if (qty === 0) {
          computedStatus = 'OUT_OF_STOCK';
        } else if (lowStockThreshold !== undefined && lowStockThreshold !== null) {
          if (qty <= lowStockThreshold) {
            computedStatus = 'LOW_STOCK';
          }
        } else {
          if (qty <= 10) {
            computedStatus = 'LOW_STOCK';
          }
        }

        allVariants.push({
          productId: prod.id,
          productName: prod.name,
          categoryName: categoryName,
          imageUrl: imageUrl,
          colorName: v.colorName || colorInfo?.colorName || 'N/A',
          colorCode: colorInfo?.colorCode || '#cccccc',
          size: v.size,
          stockQuantity: qty,
          lowStockThreshold: lowStockThreshold,
          status: computedStatus,
        });
      });
    });

    // Apply filter on the flat variant status
    let filteredVariants = allVariants;
    if (status && status !== '_all') {
      filteredVariants = allVariants.filter((v) => v.status === status);
    }

    // Apply sorting
    if (sortBy) {
      const dir = sortDir === 'desc' ? -1 : 1;
      const SIZE_ORDER = { 'XS': 1, 'S': 2, 'M': 3, 'L': 4, 'XL': 5, '2XL': 6, '3XL': 7, 'XXL': 8 };
      const STATUS_ORDER = { 'OUT_OF_STOCK': 1, 'LOW_STOCK': 2, 'IN_STOCK': 3 };

      filteredVariants.sort((a, b) => {
        let valA = a[sortBy];
        let valB = b[sortBy];

        if (sortBy === 'size') {
          const orderA = SIZE_ORDER[String(valA).toUpperCase()] || 99;
          const orderB = SIZE_ORDER[String(valB).toUpperCase()] || 99;
          return (orderA - orderB) * dir;
        }

        if (sortBy === 'status') {
          const orderA = STATUS_ORDER[valA] || 99;
          const orderB = STATUS_ORDER[valB] || 99;
          return (orderA - orderB) * dir;
        }

        if (valA === undefined || valA === null) valA = '';
        if (valB === undefined || valB === null) valB = '';

        if (typeof valA === 'string') {
          return valA.localeCompare(valB, 'vi', { sensitivity: 'base' }) * dir;
        }
        if (typeof valA === 'number') {
          return (valA - valB) * dir;
        }
        return 0;
      });
    }

    // Paginate results
    const totalElements = filteredVariants.length;
    const totalPages = Math.ceil(totalElements / size);
    const start = page * size;
    const paginatedItems = filteredVariants.slice(start, start + size);

    return {
      data: {
        data: {
          items: paginatedItems,
          totalElements,
          totalPages,
          page,
          size,
        },
      },
    };
  },
};
