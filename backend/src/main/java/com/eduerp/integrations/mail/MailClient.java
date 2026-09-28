package com.eduerp.integrations.mail;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;


@Component
public class MailClient {

    private final JavaMailSender mailSender;

    MailClient(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Email văn bản thuần — dùng khi không cần bố cục HTML. */
    public void send(String from, String to, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * Email HTML kèm bản văn bản thuần thay thế (multipart/alternative) — mail client nào không
     * hiển thị được HTML (hoặc người dùng chọn xem dạng text) vẫn đọc được nội dung đầy đủ, không
     * chỉ mỗi "xem email này ở trình duyệt".
     */
    public void sendHtml(String from, String to, String subject, String html, String textFallback) {
        var mimeMessage = mailSender.createMimeMessage();
        try {
            var helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textFallback, html);
        } catch (MessagingException e) {
            throw new IllegalStateException("Không dựng được email HTML: " + subject, e);
        }
        mailSender.send(mimeMessage);
    }
}
