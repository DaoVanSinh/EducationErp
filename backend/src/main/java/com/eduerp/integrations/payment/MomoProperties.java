package com.eduerp.integrations.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Giá trị sandbox nằm trong {@code application.yml}, secret thật đọc từ biến môi trường - không
 * hardcode secret trong code (spec mục 6), mirror cách {@code storage} đã làm ở Phân hệ 2.6. */
@ConfigurationProperties(prefix = "payment.momo")
public record MomoProperties(String partnerCode, String accessKey, String secretKey, String endpoint,
        String redirectUrl, String ipnUrl) {
}
