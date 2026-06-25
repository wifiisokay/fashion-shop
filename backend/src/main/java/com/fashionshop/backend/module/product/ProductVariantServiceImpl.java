package com.fashionshop.backend.module.product;

import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.repository.CartItemRepository;
import com.fashionshop.backend.domain.repository.OrderItemRepository;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.ReturnItemRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductVariantRequest;
import com.fashionshop.backend.module.product.dto.request.StockUpdateRequest;
import com.fashionshop.backend.module.product.dto.response.ProductVariantResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductVariantServiceImpl implements ProductVariantService {

    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ProductColorRepository colorRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnItemRepository returnItemRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getByProductId(Long productId) {
        return variantRepository.findByProductId(productId)
                .stream().map(ProductVariantResponse::from).toList();
    }

    // ──────────────────────────────────────────────
    // CREATE — không đổi logic, chỉ thêm guard tường minh
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public ProductVariantResponse create(Long productId, ProductVariantRequest request) {
        Product product = findProductOrThrow(productId);
        ProductColor color = findColorOrThrow(request.getColorId(), productId);

        // [FIX] Guard tường minh: stockQuantity bắt buộc khi tạo mới.
        // ProductVariantRequest đã bỏ @NotNull để dùng chung được cho update(),
        // nên validate thủ công ở đây.
        if (request.getStockQuantity() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "Số lượng tồn kho không được để trống khi tạo biến thể mới");
        }

        // Check UNIQUE(color_id, size)
        if (variantRepository.existsByColorIdAndSize(color.getId(), request.getSize())) {
            // [FIX B1] VARIANT_NOT_FOUND -> VARIANT_DUPLICATED (đúng ngữ nghĩa lỗi CONFLICT)
            throw new BusinessException(ErrorCode.VARIANT_DUPLICATED, HttpStatus.CONFLICT,
                    "Đã tồn tại biến thể cùng màu '" + color.getColorName() + "' và size '" + request.getSize() + "'");
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .color(color)
                .size(request.getSize().trim())
                .stockQuantity(request.getStockQuantity())
                .priceAdjustment(request.getPrice())
                .build();

        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    // ──────────────────────────────────────────────
    // UPDATE — sửa color/size/price, KHÔNG sửa stockQuantity
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public ProductVariantResponse update(Long productId, Long variantId, ProductVariantRequest request) {
        // [FIX #2] Dùng findByIdForUpdate() thay vì findById() thường.
        // Lý do: update() và OrderServiceImpl.decreaseStock() đều thao tác trên
        // cùng entity ProductVariant. Nếu update() không lock, có thể xảy ra
        // lost-update khi admin sửa giá/màu/size đúng lúc khách đặt hàng —
        // JPA dirty-checking chỉ ghi field thay đổi nên rủi ro mất stock đã giảm
        // là thấp, nhưng lock vẫn cần để đảm bảo tính nhất quán khi đọc dữ liệu
        // mới nhất trước khi so sánh điều kiện unique color+size.
        ProductVariant variant = findVariantForUpdateOrThrow(variantId, productId);
        ProductColor color = findColorOrThrow(request.getColorId(), productId);

        // [FIX #1] Chặn đổi color/size nếu biến thể đã phát sinh đơn hàng hoặc
        // yêu cầu đổi/trả — tránh "biến dạng ngầm" dữ liệu lịch sử nghiệp vụ.
        // (OrderItem có lưu snapshot tên/màu/size riêng nên không vỡ dữ liệu hiển thị,
        // nhưng về nghiệp vụ, variant đang đại diện cho một SKU đã từng được bán
        // không nên đổi sang màu/size khác mà không qua quy trình riêng.)
        boolean colorOrSizeChanged = !Objects.equals(variant.getColor().getId(), color.getId())
                || !Objects.equals(variant.getSize(), request.getSize().trim());

        if (colorOrSizeChanged) {
            boolean hasOrderHistory = orderItemRepository.existsByVariantId(variantId)
                    || returnItemRepository.existsByVariantId(variantId);
            if (hasOrderHistory) {
                throw new BusinessException(ErrorCode.VARIANT_IN_CART, HttpStatus.CONFLICT,
                        "Biến thể đã phát sinh đơn hàng hoặc yêu cầu đổi/trả, không thể đổi màu/size. "
                                + "Bạn vẫn có thể cập nhật giá hoặc tồn kho.");
            }
        }

        // Check UNIQUE trừ chính variant đang sửa
        if (variantRepository.existsByColorIdAndSizeAndIdNot(color.getId(), request.getSize(), variantId)) {
            // [FIX B1] VARIANT_NOT_FOUND -> VARIANT_DUPLICATED
            throw new BusinessException(ErrorCode.VARIANT_DUPLICATED, HttpStatus.CONFLICT,
                    "Đã tồn tại biến thể cùng màu '" + color.getColorName() + "' và size '" + request.getSize() + "'");
        }

        variant.setColor(color);
        variant.setSize(request.getSize().trim());
        variant.setPriceAdjustment(request.getPrice());
        // [FIX B3] KHÔNG set stockQuantity ở đây nữa.
        // Tồn kho chỉ được thay đổi qua PATCH /stock (xem updateStock() bên dưới).
        // request.getStockQuantity() bị bỏ qua hoàn toàn trong update().

        return ProductVariantResponse.from(variantRepository.save(variant));
    }

    // ──────────────────────────────────────────────
    // DELETE — không đổi logic, chỉ chuẩn hóa message
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(Long productId, Long variantId) {
        ProductVariant variant = findVariantOrThrow(variantId, productId);
        // Enforce RESTRICT: chặn xóa nếu variant đang có trong giỏ hàng của ai đó
        if (cartItemRepository.existsByVariantId(variantId)) {
            throw new BusinessException(ErrorCode.VARIANT_IN_CART, HttpStatus.CONFLICT,
                    "Biến thể đang có trong giỏ hàng, không thể xóa");
        }
        if (orderItemRepository.existsByVariantId(variantId)) {
            // [FIX S1] Chuẩn hóa tiếng Việt có dấu
            throw new BusinessException(ErrorCode.VARIANT_IN_CART, HttpStatus.CONFLICT,
                    "Biến thể đã phát sinh đơn hàng, không thể xóa. Vui lòng đặt tồn kho về 0 hoặc ẩn sản phẩm.");
        }
        if (returnItemRepository.existsByVariantId(variantId)) {
            // [FIX S1] Chuẩn hóa tiếng Việt có dấu
            throw new BusinessException(ErrorCode.VARIANT_IN_CART, HttpStatus.CONFLICT,
                    "Biến thể đã phát sinh yêu cầu đổi/trả, không thể xóa. Vui lòng đặt tồn kho về 0 hoặc ẩn sản phẩm.");
        }
        variantRepository.delete(variant);
    }

    // ──────────────────────────────────────────────
    // UPDATE STOCK — chỉ CỘNG THÊM, dùng atomic query
    // ──────────────────────────────────────────────

    @Override
    @Transactional
    public ProductVariantResponse updateStock(Long productId, Long variantId, StockUpdateRequest request) {
        // Xác nhận variant tồn tại và thuộc đúng product trước khi update atomic.
        // findVariantOrThrow KHÔNG lock — không cần lock ở bước kiểm tra này vì
        // increaseStock() bên dưới đã tự atomic (UPDATE ... SET stock = stock + :qty).
        findVariantOrThrow(variantId, productId);

        // [FIX B2 + S2] Dùng increaseStock() atomic thay vì read-modify-write.
        // Tránh lost-update khi nhiều request PATCH /stock chạy đồng thời,
        // hoặc khi chạy đồng thời với decreaseStock() từ đơn hàng.
        int updated = variantRepository.increaseStock(variantId, request.getAddedStock());
        if (updated == 0) {
            // Biến thể có thể đã bị xóa giữa lúc findVariantOrThrow() và increaseStock()
            // (khoảng hở rất nhỏ, nhưng vẫn nên xử lý tường minh thay vì im lặng).
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Biến thể không còn tồn tại, vui lòng tải lại trang");
        }

        log.info("[VARIANT_STOCK] variantId={} addedStock={}", variantId, request.getAddedStock());

        // Đọc lại để trả về state mới nhất (increaseStock không trả entity).
        ProductVariant updatedVariant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));
        return ProductVariantResponse.from(updatedVariant);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));
    }

    private ProductColor findColorOrThrow(Long colorId, Long productId) {
        // [FIX B4] PRODUCT_NOT_FOUND -> COLOR_NOT_FOUND / COLOR_NOT_BELONG
        ProductColor color = colorRepository.findById(colorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLOR_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Không tìm thấy màu sắc"));
        if (!color.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.COLOR_NOT_BELONG, HttpStatus.NOT_FOUND,
                    "Màu sắc không thuộc sản phẩm này");
        }
        return color;
    }

    private ProductVariant findVariantOrThrow(Long variantId, Long productId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Biến thể không thuộc sản phẩm này");
        }
        return variant;
    }

    /**
     * [FIX #2] Biến thể của findVariantOrThrow() nhưng dùng PESSIMISTIC_WRITE lock.
     * Dùng riêng cho update() — nơi sửa color/size/price, cần đảm bảo không đọc
     * dữ liệu cũ trong lúc một transaction khác (đặt hàng) đang ghi vào cùng row.
     */
    private ProductVariant findVariantForUpdateOrThrow(Long variantId, Long productId) {
        ProductVariant variant = variantRepository.findByIdForUpdate(variantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND));
        if (!variant.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.VARIANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Biến thể không thuộc sản phẩm này");
        }
        return variant;
    }
}