/**
 * Kênh thông báo real-time qua SSE. Cơ chế thuần: không biết sự kiện nghiệp vụ nào kích hoạt nó —
 * module nghiệp vụ (vd access) publish domain event, một listener (Task 15) dịch sang gọi
 * {@link com.eduerp.integrations.notification.NotificationManagement#push}. Giống hệt vai trò của
 * {@code integrations.mail}: cơ chế gửi, không phải nội dung.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Notification")
package com.eduerp.integrations.notification;
