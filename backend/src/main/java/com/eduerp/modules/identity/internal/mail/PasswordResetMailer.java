package com.eduerp.modules.identity.internal.mail;

import com.eduerp.integrations.mail.MailClient;
import com.eduerp.modules.identity.IdentityProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class PasswordResetMailer {

    /**
     * Đường dẫn template tính từ gốc classpath (không có phần mở rộng {@code .html}), nằm ngay
     * trong cây package của module này — xem {@code spring.thymeleaf.prefix} trong application.yml.
     */
    private static final String TEMPLATE = "com/eduerp/modules/identity/internal/mail/templates/password-reset";

    private static final String SUBJECT = "Đặt lại mật khẩu EduERP";

    private final MailClient mailClient;
    private final TemplateEngine templateEngine;
    private final IdentityProperties properties;

    PasswordResetMailer(MailClient mailClient, TemplateEngine templateEngine, IdentityProperties properties) {
        this.mailClient = mailClient;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void sendResetLink(String toEmail, String token) {
        String resetUrl = properties.frontendResetUrl() + "?token=" + token;
        long expiresInMinutes = properties.passwordResetTtl().toMinutes();

        var context = new Context();
        context.setVariable("resetUrl", resetUrl);
        context.setVariable("expiresInMinutes", expiresInMinutes);
        String html = templateEngine.process(TEMPLATE, context);

        String textFallback = "Nhấn vào liên kết sau để đặt lại mật khẩu (hết hạn sau " + expiresInMinutes
                + " phút): " + resetUrl;

        mailClient.sendHtml(properties.mailFrom(), toEmail, SUBJECT, html, textFallback);
    }
}
