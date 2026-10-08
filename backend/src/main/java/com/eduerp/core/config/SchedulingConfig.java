package com.eduerp.core.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật cơ chế {@code @Scheduled} một lần cho toàn app - mirror cách
 * {@code core.security.SecurityBootstrapConfig} bật Spring Security: core bật cơ chế, module tự khai
 * job của mình ({@code modules.billing.internal.OverdueInvoiceScheduler} là job đầu tiên). Không có
 * lớp này thì {@code @Scheduled} bị Spring bỏ qua im lặng - job không bao giờ chạy mà cũng không
 * báo lỗi.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
