package com.eduerp.modules.identity.internal.mail;

import com.eduerp.integrations.mail.MailClient;
import com.eduerp.modules.identity.IdentityProperties;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class AccountInviteMailer {

    private static final String TEMPLATE = "com/eduerp/modules/identity/internal/mail/templates/account-invite";
    private static final String SUBJECT = "Tài khoản EduERP của bạn đã được tạo";

    private final MailClient mailClient;
    private final TemplateEngine templateEngine;
    private final IdentityProperties properties;

    AccountInviteMailer(MailClient mailClient, TemplateEngine templateEngine, IdentityProperties properties) {
        this.mailClient = mailClient;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    public void sendInvite(String toEmail, String fullName, String temporaryPassword) {
        long expiresInDays = properties.accountInviteTtl().toDays();

        var context = new Context();
        context.setVariable("fullName", fullName);
        context.setVariable("email", toEmail);
        context.setVariable("temporaryPassword", temporaryPassword);
        context.setVariable("expiresInDays", expiresInDays);
        String html = templateEngine.process(TEMPLATE, context);

        String textFallback = "Xin chào " + fullName + ", tài khoản EduERP của bạn: " + toEmail
                + ". Mật khẩu tạm: " + temporaryPassword + " (hết hạn sau " + expiresInDays + " ngày).";

        mailClient.sendHtml(properties.mailFrom(), toEmail, SUBJECT, html, textFallback);
    }
}
