package com.fashionshop.backend.module.product;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.product.dto.request.CategoryRequest;
import com.fashionshop.backend.module.product.dto.response.CategoryResponse;
import com.fashionshop.backend.module.product.dto.response.CategoryTreeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private CategoryService categoryService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
            .id(1L).email("admin@test.com")
            .role(Role.ADMIN).status(UserStatus.ACTIVE)
            .password("encoded").fullName("Admin User")
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
    // GET /api/categories (Public)
    // ================================================================

    @Test
    void getTree_returns200_withTreeData() throws Exception {
        CategoryResponse child = CategoryResponse.builder()
            .id(3).name("Áo Nam").slug("ao-nam").build();
        CategoryTreeResponse root = CategoryTreeResponse.builder()
            .id(1).name("Áo").slug("ao").children(List.of(child)).build();
        when(categoryService.getTree()).thenReturn(List.of(root));

        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data[0].name").value("Áo"))
            .andExpect(jsonPath("$.data[0].children[0].name").value("Áo Nam"));
    }

    // ================================================================
    // GET /api/categories/{id} (Public)
    // ================================================================

    @Test
    void getById_returns200_whenFound() throws Exception {
        CategoryResponse resp = CategoryResponse.builder()
            .id(1).name("Áo").slug("ao").build();
        when(categoryService.getById(1)).thenReturn(resp);

        mockMvc.perform(get("/api/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Áo"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        when(categoryService.getById(99))
            .thenThrow(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/categories/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ================================================================
    // POST /api/admin/categories (Admin)
    // ================================================================

    @Test
    void create_returns201_withValidBody() throws Exception {
        CategoryResponse resp = CategoryResponse.builder()
            .id(10).name("Giày").slug("giay").build();
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        CategoryRequest request = buildValidRequest();
        request.setName("");

        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_returns409_whenSlugExists() throws Exception {
        when(categoryService.create(any(CategoryRequest.class)))
            .thenThrow(new BusinessException(ErrorCode.CATEGORY_SLUG_EXISTS, HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/admin/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isConflict());
    }

    // ================================================================
    // PUT /api/admin/categories/{id} (Admin)
    // ================================================================

    @Test
    void update_returns200_whenValid() throws Exception {
        CategoryResponse resp = CategoryResponse.builder()
            .id(1).name("Áo Updated").slug("ao-updated").build();
        when(categoryService.update(eq(1), any(CategoryRequest.class))).thenReturn(resp);

        mockMvc.perform(put("/api/admin/categories/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        when(categoryService.update(eq(99), any(CategoryRequest.class)))
            .thenThrow(new BusinessException(ErrorCode.CATEGORY_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/admin/categories/99")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // DELETE /api/admin/categories/{id} (Admin)
    // ================================================================

    @Test
    void delete_returns200_whenSuccess() throws Exception {
        doNothing().when(categoryService).delete(1);

        mockMvc.perform(delete("/api/admin/categories/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void delete_returns409_whenHasProducts() throws Exception {
        doThrow(new BusinessException(ErrorCode.CATEGORY_HAS_ACTIVE_PRODUCTS, HttpStatus.CONFLICT))
            .when(categoryService).delete(1);

        mockMvc.perform(delete("/api/admin/categories/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ================================================================
    // Fixture
    // ================================================================

    private CategoryRequest buildValidRequest() {
        CategoryRequest r = new CategoryRequest();
        r.setName("Giày");
        r.setDescription("Danh mục giày dép");
        return r;
    }
}
