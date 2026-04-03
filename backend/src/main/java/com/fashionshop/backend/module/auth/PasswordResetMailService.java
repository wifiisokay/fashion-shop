package com.fashionshop.backend.module.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetMailService {

    private final JavaMailSender mailSender;

    @Value("${auth.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${auth.mail.from:no-reply@fashion-shop.local}")
    private String fromEmail;

    @Value("${auth.reset-password-url-base:http://localhost:5173/reset-password}")
    private String resetPasswordUrlBase;

    public void sendResetPasswordEmail(String toEmail, String resetToken) {
        String resetLink = UriComponentsBuilder
            .fromUriString(resetPasswordUrlBase)
            .queryParam("token", resetToken)
            .build()
            .toUriString();

        if (!mailEnabled) {
            // Dev fallback: still expose link in logs for local testing.
            log.warn("[DEV] Password reset link for {}: {}", toEmail, resetLink);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Dat lai mat khau - Fashion Shop");
        message.setText(
            "Xin chao,\n\n" +
            "Ban vua yeu cau dat lai mat khau.\n" +
            "Nhan vao link ben duoi de tiep tuc:\n" +
            resetLink + "\n\n" +
            "Link co hieu luc trong 15 phut va chi dung duoc 1 lan.\n" +
            "Neu ban khong yeu cau thao tac nay, hay bo qua email nay.\n\n" +
            "Fashion Shop"
        );

        mailSender.send(message);
    }
}
