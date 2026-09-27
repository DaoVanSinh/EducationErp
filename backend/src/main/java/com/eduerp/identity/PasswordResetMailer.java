package com.eduerp.identity;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final IdentityProperties properties;

    PasswordResetMailer(JavaMailSender mailSender, IdentityProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    void sendResetLink(String toEmail, String token) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(toEmail);
        message.setSubject("Đặt lại mật khẩu EduERP");
        message.setText("Nhấn vào liên kết sau để đặt lại mật khẩu (hết hạn sau 30 phút): "
                + properties.frontendResetUrl() + "?token=" + token);
        mailSender.send(message);
    }
}
