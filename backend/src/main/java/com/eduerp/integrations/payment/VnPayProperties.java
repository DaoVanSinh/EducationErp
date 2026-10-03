package com.eduerp.integrations.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.vnpay")
public record VnPayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl, String ipnUrl) {
}
