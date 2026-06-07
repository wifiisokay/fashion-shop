package com.fashionshop.backend.module.order;

import com.fashionshop.backend.common.enums.CategoryRole;
import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.OrderPaymentStatus;
import com.fashionshop.backend.common.enums.OrderStatus;
import com.fashionshop.backend.common.enums.PaymentMethod;
import com.fashionshop.backend.common.enums.PaymentStatus;
import com.fashionshop.backend.common.enums.ProductStatus;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.domain.Address;
import com.fashionshop.backend.domain.Category;
import com.fashionshop.backend.domain.Order;
import com.fashionshop.backend.domain.OrderItem;
import com.fashionshop.backend.domain.Payment;
import com.fashionshop.backend.domain.Product;
import com.fashionshop.backend.domain.ProductColor;
import com.fashionshop.backend.domain.ProductVariant;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.domain.repository.AddressRepository;
import com.fashionshop.backend.domain.repository.CategoryRepository;
import com.fashionshop.backend.domain.repository.OrderRepository;
import com.fashionshop.backend.domain.repository.PaymentRepository;
import com.fashionshop.backend.domain.repository.ProductColorRepository;
import com.fashionshop.backend.domain.repository.ProductRepository;
import com.fashionshop.backend.domain.repository.ProductVariantRepository;
import com.fashionshop.backend.domain.repository.UserRepository;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.module.order.dto.request.CancelOrderRequest;
import com.fashionshop.backend.module.order.dto.request.CreateOrderRequest;
import com.fashionshop.backend.module.order.dto.request.OrderItemRequest;
import com.fashionshop.backend.module.order.dto.response.CreateOrderResponse;
import com.fashionshop.backend.module.payment.PaymentService;
import com.fashionshop.backend.module.payment.VnPayService;
import com.fashionshop.backend.module.shipping.CheckoutShippingFeeResult;
import com.fashionshop.backend.module.shipping.ShippingCalculationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class OrderPaymentStockFlowIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentStockFlowIntegrationTest.class);

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired UserRepository userRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired ProductColorRepository colorRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;

    @MockBean ShippingCalculationService shippingCalculationService;
    @MockBean VnPayService vnPayService;

    private final List<Long> paymentIds = new ArrayList<>();
    private final List<Long> orderIds = new ArrayList<>();
    private final List<Long> variantIds = new ArrayList<>();
    private final List<Long> colorIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Integer> categoryIds = new ArrayList<>();
    private final List<Long> addressIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();

    private User customer;
    private User secondCustomer;
    private Address customerAddress;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        when(shippingCalculationService.calculateCheckoutFee(any(), any(), org.mockito.ArgumentMatchers.<Map<Long, Integer>>any()))
            .thenReturn(new CheckoutShippingFeeResult(
                "TEST", 30_000, 500, 30, 20, 8,
                0, 3, "3 ngay", LocalDate.now().plusDays(3), false));
        when(vnPayService.verifySignature(any(), anyString())).thenReturn(true);

        String runId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        customer = saveUser("it-customer-" + runId + "@test.local", Role.CUSTOMER);
        secondCustomer = saveUser("it-customer2-" + runId + "@test.local", Role.CUSTOMER);
        customerAddress = saveAddress(customer);
        saveAddress(secondCustomer);
        variant = saveCatalog(runId, 10);
    }

    @AfterEach
    void tearDown() {
        orderIds.forEach(id -> paymentRepository.findByOrderId(id).ifPresent(paymentRepository::delete));
        paymentIds.forEach(id -> paymentRepository.findById(id).ifPresent(paymentRepository::delete));
        orderIds.forEach(id -> orderRepository.findById(id).ifPresent(orderRepository::delete));
        variantIds.forEach(id -> variantRepository.findById(id).ifPresent(variantRepository::delete));
        colorIds.forEach(id -> colorRepository.findById(id).ifPresent(colorRepository::delete));
        productIds.forEach(id -> productRepository.findById(id).ifPresent(productRepository::delete));
        categoryIds.forEach(id -> categoryRepository.findById(id).ifPresent(categoryRepository::delete));
        addressIds.forEach(id -> addressRepository.findById(id).ifPresent(addressRepository::delete));
        userIds.forEach(id -> userRepository.findById(id).ifPresent(userRepository::delete));
    }

    @Test
    @DisplayName("COD PENDING customer cancel restores stock exactly once")
    void codPendingCustomerCancel_restoresStockOnce() {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.COD, OrderStatus.PENDING,
            OrderPaymentStatus.UNPAID, PaymentStatus.PENDING, 1);

        orderService.cancelOrder(customer.getId(), order.getId(), cancelRequest("customer cancel COD"));

        assertState("COD PENDING customer cancel", order.getId(), PaymentStatus.FAILED,
            OrderStatus.CANCELLED, OrderPaymentStatus.UNPAID, 10);
    }

    @Test
    @DisplayName("VNPay AWAITING_PAYMENT customer cancel sets payment FAILED and restores stock")
    void vnpayAwaitingCustomerCancel_marksPaymentFailedAndRestoresStock() {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.VNPAY, OrderStatus.AWAITING_PAYMENT,
            OrderPaymentStatus.UNPAID, PaymentStatus.PENDING, 1);

        orderService.cancelOrder(customer.getId(), order.getId(), cancelRequest("customer cancel VNPay unpaid"));

        assertState("VNPay unpaid customer cancel", order.getId(), PaymentStatus.FAILED,
            OrderStatus.CANCELLED, OrderPaymentStatus.UNPAID, 10);
    }

    @Test
    @DisplayName("VNPay PAID customer cancel is blocked and stock is unchanged")
    void vnpayPaidCustomerCancel_blockedStockUnchanged() {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.VNPAY, OrderStatus.PENDING,
            OrderPaymentStatus.PAID, PaymentStatus.SUCCESS, 1);

        assertThatThrownBy(() -> orderService.cancelOrder(customer.getId(), order.getId(),
            cancelRequest("customer tries cancel paid VNPay")))
            .as("VNPay PAID customer cancel must fail with ORDER_CANNOT_CANCEL")
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode().getCode())
                .isEqualTo("ORDER_002"));

        assertState("VNPay paid customer cancel blocked", order.getId(), PaymentStatus.SUCCESS,
            OrderStatus.PENDING, OrderPaymentStatus.PAID, 9);
    }

    @Test
    @DisplayName("Staff cancel VNPay PAID refunds payment, cancels order and restores stock")
    void staffCancelVnPayPaid_refundsCancelsAndRestoresStock() {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.VNPAY, OrderStatus.PENDING,
            OrderPaymentStatus.PAID, PaymentStatus.SUCCESS, 1);

        orderService.staffCancelOrder(order.getId(), cancelRequest("staff approved refund"));

        assertState("staff cancel VNPay paid", order.getId(), PaymentStatus.REFUNDED,
            OrderStatus.CANCELLED, OrderPaymentStatus.REFUNDED, 10);
    }

    @Test
    @DisplayName("Concurrent customer cancel and staff cancel restore stock only once")
    void concurrentCustomerAndStaffCancel_restoreStockOnlyOnce() throws Exception {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.COD, OrderStatus.PENDING,
            OrderPaymentStatus.UNPAID, PaymentStatus.PENDING, 1);

        ConcurrentResult result = runConcurrently(
            () -> {
                orderService.cancelOrder(customer.getId(), order.getId(), cancelRequest("customer concurrent cancel"));
                return "customer-ok";
            },
            () -> {
                orderService.staffCancelOrder(order.getId(), cancelRequest("staff concurrent cancel"));
                return "staff-ok";
            });

        log.info("[ORDER_FLOW_IT] concurrent cancel results: success={}, failures={}",
            result.successCount(), result.failures());
        assertThat(result.successCount()).as("At least one cancel request should complete").isGreaterThanOrEqualTo(1);
        assertState("concurrent customer/staff cancel", order.getId(), PaymentStatus.FAILED,
            OrderStatus.CANCELLED, OrderPaymentStatus.UNPAID, 10);
    }

    @Test
    @DisplayName("Concurrent checkout with same stock=1 lets only one user create order")
    void concurrentCheckoutSameVariantStockOne_onlyOneOrderSucceeds() throws Exception {
        ProductVariant stockOneVariant = saveCatalog("stock1-" + UUID.randomUUID().toString().substring(0, 8), 1);
        Address secondAddress = addressRepository.findById(addressIds.get(1)).orElseThrow();

        ConcurrentResult result = runConcurrently(
            () -> {
                CreateOrderResponse response = orderService.createOrder(customer.getId(),
                    createOrderRequest(customerAddress.getId(), stockOneVariant.getId()), null);
                trackOrder(response.getOrderId());
                return "checkout-1-ok";
            },
            () -> {
                CreateOrderResponse response = orderService.createOrder(secondCustomer.getId(),
                    createOrderRequest(secondAddress.getId(), stockOneVariant.getId()), null);
                trackOrder(response.getOrderId());
                return "checkout-2-ok";
            });

        ProductVariant refreshed = variantRepository.findById(stockOneVariant.getId()).orElseThrow();
        log.info("[ORDER_FLOW_IT] concurrent checkout results: success={}, failures={}, finalStock={}",
            result.successCount(), result.failures(), refreshed.getStockQuantity());

        assertThat(result.successCount()).as("Only one checkout may succeed when stock=1").isEqualTo(1);
        assertThat(result.failures()).as("One checkout should fail because stock is exhausted").hasSize(1);
        assertThat(refreshed.getStockQuantity()).as("Final stock after one successful checkout").isZero();
    }

    @Test
    @DisplayName("VNPay IPN success after CANCELLED order does not revive order or payment")
    void ipnSuccessAfterCancelledOrder_doesNotReviveOrderOrPayment() {
        Order order = seedPlacedOrder(customer, variant, PaymentMethod.VNPAY, OrderStatus.CANCELLED,
            OrderPaymentStatus.UNPAID, PaymentStatus.FAILED, 1);
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElseThrow();
        payment.setVnpayTxnRef("txn-" + UUID.randomUUID());
        payment = paymentRepository.saveAndFlush(payment);

        Map<String, String> ipn = Map.of(
            "vnp_TxnRef", payment.getVnpayTxnRef(),
            "vnp_ResponseCode", "00",
            "vnp_Amount", order.getTotalAmount().multiply(BigDecimal.valueOf(100)).toPlainString(),
            "vnp_TransactionNo", "VNPTEST",
            "vnp_BankCode", "NCB",
            "vnp_SecureHash", "valid");
        when(vnPayService.verifySignature(eq(ipn), eq("valid"))).thenReturn(true);

        Map<String, String> result = paymentService.handleIpn(ipn);

        assertThat(result.get("RspCode")).as("IPN should be idempotent for processed payment").isEqualTo("02");
        assertState("IPN success after cancelled", order.getId(), PaymentStatus.FAILED,
            OrderStatus.CANCELLED, OrderPaymentStatus.UNPAID, 9);
    }

    private ConcurrentResult runConcurrently(Callable<String> first, Callable<String> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        List<String> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            Future<?> f1 = executor.submit(wrapConcurrent(first, ready, start, successCount, failures));
            Future<?> f2 = executor.submit(wrapConcurrent(second, ready, start, successCount, failures));
            assertThat(ready.await(5, TimeUnit.SECONDS)).as("Both concurrent tasks should be ready").isTrue();
            start.countDown();
            f1.get(20, TimeUnit.SECONDS);
            f2.get(20, TimeUnit.SECONDS);
            return new ConcurrentResult(successCount.get(), List.copyOf(failures));
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Void> wrapConcurrent(Callable<String> task, CountDownLatch ready, CountDownLatch start,
                                          AtomicInteger successCount, List<String> failures) {
        return () -> {
            ready.countDown();
            assertThat(start.await(5, TimeUnit.SECONDS)).as("Concurrent task should receive start signal").isTrue();
            try {
                String label = task.call();
                successCount.incrementAndGet();
                log.info("[ORDER_FLOW_IT] concurrent task success: {}", label);
            } catch (Exception ex) {
                failures.add(ex.getClass().getSimpleName() + ": " + ex.getMessage());
                log.info("[ORDER_FLOW_IT] concurrent task failed as expected/allowed: {} - {}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            }
            return null;
        };
    }

    private void assertState(String scenario, Long orderId, PaymentStatus expectedPaymentStatus,
                             OrderStatus expectedOrderStatus, OrderPaymentStatus expectedOrderPaymentStatus,
                             int expectedStock) {
        Order refreshedOrder = orderRepository.findById(orderId).orElseThrow();
        Payment refreshedPayment = paymentRepository.findByOrderId(orderId).orElseThrow();
        ProductVariant refreshedVariant = variantRepository.findById(variant.getId()).orElseThrow();

        log.info("[ORDER_FLOW_IT] {} success: orderStatus={}, orderPaymentStatus={}, paymentStatus={}, stock={}",
            scenario, refreshedOrder.getStatus(), refreshedOrder.getPaymentStatus(),
            refreshedPayment.getStatus(), refreshedVariant.getStockQuantity());

        assertThat(refreshedOrder.getStatus()).as(scenario + " - order.status").isEqualTo(expectedOrderStatus);
        assertThat(refreshedOrder.getPaymentStatus()).as(scenario + " - order.paymentStatus")
            .isEqualTo(expectedOrderPaymentStatus);
        assertThat(refreshedPayment.getStatus()).as(scenario + " - payment.status").isEqualTo(expectedPaymentStatus);
        assertThat(refreshedVariant.getStockQuantity()).as(scenario + " - variant.stockQuantity")
            .isEqualTo(expectedStock);
    }

    private Order seedPlacedOrder(User user, ProductVariant orderVariant, PaymentMethod method, OrderStatus orderStatus,
                                  OrderPaymentStatus orderPaymentStatus, PaymentStatus paymentStatus, int quantity) {
        orderVariant.setStockQuantity(orderVariant.getStockQuantity() - quantity);
        variantRepository.saveAndFlush(orderVariant);

        BigDecimal unitPrice = orderVariant.getProduct().getBasePrice();
        Order order = Order.builder()
            .user(user)
            .status(orderStatus)
            .paymentMethod(method)
            .paymentStatus(orderPaymentStatus)
            .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
            .shippingFee(BigDecimal.valueOf(30_000))
            .totalAmount(unitPrice.multiply(BigDecimal.valueOf(quantity)).add(BigDecimal.valueOf(30_000)))
            .addressSnapshot(Map.of("fullName", user.getFullName(), "phone", "0912345678"))
            .build();
        OrderItem item = OrderItem.builder()
            .order(order)
            .variant(orderVariant)
            .productId(orderVariant.getProduct().getId())
            .productName(orderVariant.getProduct().getName())
            .colorName(orderVariant.getColor().getColorName())
            .size(orderVariant.getSize())
            .unitPrice(unitPrice)
            .quantity(quantity)
            .subtotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
            .build();
        order.setItems(List.of(item));
        order = orderRepository.saveAndFlush(order);
        orderIds.add(order.getId());

        Payment payment = Payment.builder()
            .order(order)
            .method(method)
            .status(paymentStatus)
            .amount(order.getTotalAmount())
            .paidAt(paymentStatus == PaymentStatus.SUCCESS ? LocalDateTime.now() : null)
            .build();
        payment = paymentRepository.saveAndFlush(payment);
        paymentIds.add(payment.getId());
        return order;
    }

    private CreateOrderRequest createOrderRequest(Long addressId, Long variantId) {
        OrderItemRequest item = new OrderItemRequest();
        item.setVariantId(variantId);
        item.setQuantity(1);

        CreateOrderRequest request = new CreateOrderRequest();
        request.setAddressId(addressId);
        request.setPaymentMethod(PaymentMethod.COD);
        request.setItems(List.of(item));
        return request;
    }

    private CancelOrderRequest cancelRequest(String reason) {
        CancelOrderRequest request = new CancelOrderRequest();
        request.setReason(reason);
        return request;
    }

    private void trackOrder(Long orderId) {
        synchronized (orderIds) {
            orderIds.add(orderId);
        }
    }

    private User saveUser(String email, Role role) {
        User user = userRepository.saveAndFlush(User.builder()
            .email(email)
            .password("{noop}password")
            .fullName("Integration " + role)
            .phone("0912345678")
            .role(role)
            .status(UserStatus.ACTIVE)
            .build());
        userIds.add(user.getId());
        return user;
    }

    private Address saveAddress(User user) {
        Address address = addressRepository.saveAndFlush(Address.builder()
            .user(user)
            .fullName(user.getFullName())
            .phone("0912345678")
            .province("Da Nang")
            .provinceCode(48)
            .district("Hai Chau")
            .districtCode(1526)
            .ward("Thach Thang")
            .wardCode("20195")
            .street("1 Test Street")
            .isDefault(true)
            .build());
        addressIds.add(address.getId());
        return address;
    }

    private ProductVariant saveCatalog(String suffix, int stock) {
        Category category = categoryRepository.saveAndFlush(Category.builder()
            .name("IT Category " + suffix)
            .slug("it-category-" + suffix)
            .role(CategoryRole.TOP)
            .build());
        categoryIds.add(category.getId());

        Product product = productRepository.saveAndFlush(Product.builder()
            .name("IT Product " + suffix)
            .description("Integration test product")
            .basePrice(BigDecimal.valueOf(100_000))
            .gender(Gender.UNISEX)
            .material("cotton")
            .estimatedWeight(500)
            .status(ProductStatus.ACTIVE)
            .category(category)
            .createdBy(customer)
            .build());
        productIds.add(product.getId());

        ProductColor color = colorRepository.saveAndFlush(ProductColor.builder()
            .product(product)
            .colorName("Black " + suffix)
            .colorCode("#000000")
            .colorFamily("dark")
            .displayOrder(0)
            .build());
        colorIds.add(color.getId());

        ProductVariant savedVariant = variantRepository.saveAndFlush(ProductVariant.builder()
            .product(product)
            .color(color)
            .size("M")
            .stockQuantity(stock)
            .priceAdjustment(BigDecimal.ZERO)
            .build());
        variantIds.add(savedVariant.getId());
        return savedVariant;
    }

    private record ConcurrentResult(int successCount, List<String> failures) {}
}
