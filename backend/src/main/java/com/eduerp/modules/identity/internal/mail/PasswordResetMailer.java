package com.eduerp.modules.identity.internal.mail;

import com.eduerp.integrations.mail.MailClient;
import com.eduerp.modules.identity.IdentityProperties;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMailer {

    private final MailClient mailClient;
    private final IdentityProperties properties;

    PasswordResetMailer(MailClient mailClient, IdentityProperties properties) {
        this.mailClient = mailClient;
        this.properties = properties;
    }

    public void sendResetLink(String toEmail, String token) {
        mailClient.send(properties.mailFrom(), toEmail, "Đặt lại mật khẩu EduERP",
                "Nhấn vào liên kết sau để đặt lại mật khẩu (hết hạn sau 30 phút): "
                        + properties.frontendResetUrl() + "?token=" + token);
    }
}
