package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.PageResponse;
import com.fashionshop.backend.common.enums.Gender;
import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.ProductRequest;
import com.fashionshop.backend.module.product.dto.request.ProductStatusRequest;
import com.fashionshop.backend.module.product.dto.response.ProductDetailResponse;
import com.fashionshop.backend.module.product.dto.response.ProductSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private ProductService productService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private User adminUser;
    private ProductDetailResponse sampleDetail;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
            .id(1L).email("admin@test.com")
            .role(Role.ADMIN).status(UserStatus.ACTIVE)
            .password("encoded").fullName("Admin User")
            .build();

        sampleDetail = ProductDetailResponse.builder()
            .id(1L).name("Áo Polo").basePrice(new BigDecimal("300000"))
            .isSale(false).gender("MALE").status("ACTIVE")
            .variants(List.of()).images(List.of())
            .build();

        UsernamePasswordAuthenticationToken auth =
            UsernamePasswordAuthenticationToken.authenticated(
                adminUser, null, adminUser.getAuthorities()
            );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ================================================================
    // POST /api/admin/products
    // ================================================================

    @Test
    void create_returns201_withValidBody() throws Exception {
        when(productService.create(any(ProductRequest.class), any(User.class)))
            .thenReturn(sampleDetail);

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        ProductRequest request = buildValidRequest();
        request.setName("");

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_returns400_whenBasePriceNull() throws Exception {
        ProductRequest request = buildValidRequest();
        request.setBasePrice(null);

        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ================================================================
    // PUT /api/admin/products/{id}
    // ================================================================

    @Test
    void update_returns200_whenValid() throws Exception {
        when(productService.update(eq(1L), any(ProductRequest.class))).thenReturn(sampleDetail);

        mockMvc.perform(put("/api/admin/products/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(productService.update(eq(99L), any(ProductRequest.class)))
            .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/admin/products/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // PATCH /api/admin/products/{id}/status
    // ================================================================

    @Test
    void updateStatus_returns200_whenValid() throws Exception {
        when(productService.updateStatus(eq(1L), any(ProductStatusRequest.class)))
            .thenReturn(sampleDetail);

        ProductStatusRequest statusReq = new ProductStatusRequest();
        statusReq.setStatus(com.fashionshop.backend.common.enums.ProductStatus.INACTIVE);

        mockMvc.perform(patch("/api/admin/products/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(statusReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateStatus_returns400_whenStatusNull() throws Exception {
        mockMvc.perform(patch("/api/admin/products/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ================================================================
    // GET /api/admin/products
    // ================================================================

    @Test
    void list_returns200_withPagination() throws Exception {
        ProductSummaryResponse summary = ProductSummaryResponse.builder()
            .id(1L).name("Áo Polo").basePrice(new BigDecimal("300000")).status("ACTIVE")
            .build();
        Page<ProductSummaryResponse> page = new PageImpl<>(List.of(summary));
        when(productService.listAdmin(any(), any(), any(), any(), eq(0), eq(20)))
            .thenReturn(PageResponse.from(page));

        mockMvc.perform(get("/api/admin/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].name").value("Áo Polo"));
    }

    // ================================================================
    // GET /api/admin/products/{id}
    // ================================================================

    @Test
    void getById_returns200_whenFound() throws Exception {
        when(productService.getByIdAdmin(1L)).thenReturn(sampleDetail);

        mockMvc.perform(get("/api/admin/products/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        when(productService.getByIdAdmin(99L))
            .thenThrow(new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/admin/products/99"))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // Fixture
    // ================================================================

    private ProductRequest buildValidRequest() {
        ProductRequest r = new ProductRequest();
        r.setName("Áo Polo Test");
        r.setDescription("Mô tả test");
        r.setBasePrice(new BigDecimal("300000"));
        r.setIsSale(false);
        r.setCategoryId(1);
        r.setGender(Gender.MALE);
        return r;
    }
}
