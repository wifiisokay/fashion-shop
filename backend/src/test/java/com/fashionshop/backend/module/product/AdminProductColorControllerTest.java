package com.fashionshop.backend.module.product;

import com.fashionshop.backend.config.JwtAuthFilter;
import com.fashionshop.backend.config.JwtService;
import com.fashionshop.backend.config.RateLimitFilter;
import com.fashionshop.backend.module.product.dto.request.ProductColorRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminProductColorController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminProductColorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private ProductColorService colorService;
    @MockitoBean private JwtAuthFilter jwtAuthFilter;
    @MockitoBean private RateLimitFilter rateLimitFilter;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    void create_withValidPayload_returns2xx() throws Exception {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Đỏ");
        request.setColorCode("#FF0000");
        request.setDisplayOrder(0);

        mockMvc.perform(post("/api/admin/products/1/colors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void create_withNullColorCode_returns2xx() throws Exception {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Đỏ");
        request.setColorCode(null);
        request.setDisplayOrder(0);

        mockMvc.perform(post("/api/admin/products/1/colors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void create_withEmptyColorCode_returns2xx() throws Exception {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Đỏ");
        request.setColorCode("");
        request.setDisplayOrder(0);

        mockMvc.perform(post("/api/admin/products/1/colors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }

    @Test
    void create_withBlankColorName_returns400() throws Exception {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("");
        request.setColorCode("#FF0000");

        mockMvc.perform(post("/api/admin/products/1/colors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void create_withLongColorCode_returns400() throws Exception {
        ProductColorRequest request = new ProductColorRequest();
        request.setColorName("Đỏ");
        request.setColorCode("#FF00000"); // 8 chars

        mockMvc.perform(post("/api/admin/products/1/colors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
