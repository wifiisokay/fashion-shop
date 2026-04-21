package com.fashionshop.backend.module.user;

import com.fashionshop.backend.common.enums.Role;
import com.fashionshop.backend.common.enums.UserStatus;
import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.domain.User;
import com.fashionshop.backend.exception.BusinessException;
import com.fashionshop.backend.exception.ErrorCode;
import com.fashionshop.backend.module.user.dto.request.AddressRequest;
import com.fashionshop.backend.module.user.dto.response.AddressResponse;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice test cho AddressController.
 *
 * Tại sao SecurityContextHolder thay vì .with(user()):
 *   - @AutoConfigureMockMvc(addFilters = false) tắt toàn bộ security filter chain
 *   - .with(user(mockUser)) ghi auth vào HTTP session
 *   - SecurityContextHolderFilter (bị tắt) mới chuyển session → SecurityContextHolder
 *   - Không có filter → SecurityContextHolder rỗng → @AuthenticationPrincipal = null
 *
 *   Fix: Set SecurityContextHolder trực tiếp trong @BeforeEach, clear trong @AfterEach.
 *   Điều này đảm bảo AuthenticationPrincipalArgumentResolver (MVC layer, không phải filter)
 *   luôn đọc được domain User khi xử lý request.
 */
@WebMvcTest(AddressController.class)
@AutoConfigureMockMvc(addFilters = false)
class AddressControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    // ==== Bean của controller ====
    @MockitoBean private AddressService addressService;

    // ==== Infrastructure beans để SecurityConfig boot thành công ====
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    private User mockUser;
    private AddressResponse sampleResponse;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
            .id(1L).email("test@test.com")
            .role(Role.CUSTOMER).status(UserStatus.ACTIVE)
            .password("encoded").fullName("Test User")
            .build();

        sampleResponse = AddressResponse.builder()
            .id(1L)
            .fullName("Nguyễn Văn A").phone("0912345678")
            .province("Hà Nội").provinceCode(1)
            .district("Ba Đình").districtCode(1)
            .ward("Phúc Xá").wardCode("00031")
            .street("123 Đường ABC")
            .isDefault(true)
            .createdAt(LocalDateTime.now())
            .build();

        // Set SecurityContext trực tiếp — hoạt động kể cả khi addFilters=false
        // vì AuthenticationPrincipalArgumentResolver đọc từ SecurityContextHolder (MVC layer),
        // không phải từ session (filter layer).
        UsernamePasswordAuthenticationToken auth =
            UsernamePasswordAuthenticationToken.authenticated(
                mockUser, null, mockUser.getAuthorities()
            );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        // Tránh SecurityContext leak giữa các test
        SecurityContextHolder.clearContext();
    }

    // ================================================================
    // GET /api/user/addresses
    // ================================================================

    @Test
    void getAddresses_returns200_whenAuthenticated() throws Exception {
        AddressResponse second = AddressResponse.builder().id(2L).isDefault(false).build();
        when(addressService.getAddresses(1L)).thenReturn(List.of(sampleResponse, second));

        mockMvc.perform(get("/api/user/addresses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    // ================================================================
    // GET /api/user/addresses/{id}
    // ================================================================

    @Test
    void getAddressById_returns200_whenFound() throws Exception {
        when(addressService.getAddressById(1L, 1L)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/user/addresses/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void getAddressById_returns404_whenNotFound() throws Exception {
        when(addressService.getAddressById(99L, 1L))
            .thenThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/user/addresses/99"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ================================================================
    // POST /api/user/addresses
    // ================================================================

    @Test
    void createAddress_returns201_withValidBody() throws Exception {
        when(addressService.createAddress(eq(1L), any(AddressRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/user/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1L))
            .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void createAddress_returns400_whenMissingFullName() throws Exception {
        AddressRequest request = buildValidRequest();
        request.setFullName("");

        mockMvc.perform(post("/api/user/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createAddress_returns400_whenPhoneInvalid() throws Exception {
        AddressRequest request = buildValidRequest();
        request.setPhone("012345");

        mockMvc.perform(post("/api/user/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAddress_returns400_whenMissingWardCode() throws Exception {
        AddressRequest request = buildValidRequest();
        request.setWardCode("");

        mockMvc.perform(post("/api/user/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ================================================================
    // PUT /api/user/addresses/{id}
    // ================================================================

    @Test
    void updateAddress_returns200_whenValid() throws Exception {
        AddressResponse updated = AddressResponse.builder().id(1L).fullName("Tên Mới").build();
        when(addressService.updateAddress(eq(1L), eq(1L), any(AddressRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/user/addresses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void updateAddress_returns404_whenNotOwned() throws Exception {
        when(addressService.updateAddress(eq(1L), eq(1L), any()))
            .thenThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(put("/api/user/addresses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // DELETE /api/user/addresses/{id}
    // ================================================================

    @Test
    void deleteAddress_returns200_whenSuccess() throws Exception {
        doNothing().when(addressService).deleteAddress(1L, 1L);

        mockMvc.perform(delete("/api/user/addresses/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void deleteAddress_returns404_whenNotOwned() throws Exception {
        doThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND))
            .when(addressService).deleteAddress(1L, 1L);

        mockMvc.perform(delete("/api/user/addresses/1"))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // PATCH /api/user/addresses/{id}/default
    // ================================================================

    @Test
    void setDefault_returns200_whenSuccess() throws Exception {
        when(addressService.setDefault(1L, 1L)).thenReturn(sampleResponse);

        mockMvc.perform(patch("/api/user/addresses/1/default"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isDefault").value(true));
    }

    @Test
    void setDefault_returns404_whenNotOwned() throws Exception {
        when(addressService.setDefault(99L, 1L))
            .thenThrow(new BusinessException(ErrorCode.ADDRESS_NOT_FOUND, HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/user/addresses/99/default"))
            .andExpect(status().isNotFound());
    }

    // ================================================================
    // Fixture
    // ================================================================

    private AddressRequest buildValidRequest() {
        AddressRequest r = new AddressRequest();
        r.setFullName("Nguyễn Văn A");
        r.setPhone("0912345678");
        r.setProvince("Hà Nội");
        r.setProvinceCode(1);
        r.setDistrict("Ba Đình");
        r.setDistrictCode(1);
        r.setWard("Phúc Xá");
        r.setWardCode("00031");
        r.setStreet("123 Đường ABC");
        return r;
    }
}
