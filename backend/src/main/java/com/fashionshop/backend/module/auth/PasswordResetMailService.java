package com.fashionshop.backend.module.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Gửi email đặt lại mật khẩu.
 *
 * Dùng MimeMessage + MimeMessageHelper với encoding UTF-8 để
 * hiển thị tiếng Việt có dấu đúng trên mọi email client.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${auth.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${auth.mail.from:}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${auth.reset-password-url-base:http://localhost:5173/reset-password}")
    private String resetPasswordUrlBase;

    public boolean sendResetPasswordEmail(String toEmail, String resetToken) {
        String resetLink = UriComponentsBuilder
            .fromUriString(resetPasswordUrlBase)
            .queryParam("token", resetToken)
            .build()
            .toUriString();

        if (!mailEnabled) {
            log.warn("[DEV] Password reset link for {}: {}", toEmail, resetLink);
            return false;
        }

        String effectiveFrom = (fromEmail == null || fromEmail.isBlank()) ? mailUsername : fromEmail;

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // MimeMessageHelper với UTF-8 — hỗ trợ tiếng Việt có dấu
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(effectiveFrom);
            helper.setTo(toEmail);
            helper.setSubject("Đặt lại mật khẩu - Fashion Shop");
            helper.setText(buildEmailBody(resetLink), false);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", toEmail);
            return true;
        } catch (MessagingException ex) {
            log.error("Failed to send password reset email to: {}", toEmail, ex);
            log.warn("[DEV] Password reset link for {}: {}", toEmail, resetLink);
            return false;
        }
    }

    private String buildEmailBody(String resetLink) {
        return """
                Xin chào,

                Bạn vừa yêu cầu đặt lại mật khẩu cho tài khoản Fashion Shop.
                Nhấn vào liên kết bên dưới để tiếp tục:

                %s

                Liên kết có hiệu lực trong 15 phút và chỉ dùng được 1 lần.
                Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email này.

                Trân trọng,
                Fashion Shop
                """.formatted(resetLink);
    }
}
