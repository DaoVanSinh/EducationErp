package com.eduerp.integrations.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Bọc {@code JavaMailSender} của Spring Boot — không biết "from" nào hợp lý cho email nào, chỉ gửi. */
@Component
public class MailClient {

    private final JavaMailSender mailSender;

    MailClient(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void send(String from, String to, String subject, String text) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
