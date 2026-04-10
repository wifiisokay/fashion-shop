package com.fashionshop.backend.module.auth;

import com.fashionshop.backend.module.auth.dto.response.AuthResponse;

/**
 * Kết quả trả về nội bộ từ register/login.
 * Service generate token ngay bên trong — Controller chỉ cần set cookie.
 * Không expose token ra khỏi module auth (không có trong body response).
 */
public record LoginResult(AuthResponse userInfo, String token) {
}
