/**
 * Gửi email qua SMTP. Cơ chế thuần: không biết nội dung, chủ đề hay nghĩa nghiệp vụ của bất kỳ email
 * nào — module nghiệp vụ (vd {@code identity}) soạn nội dung rồi gọi
 * {@link com.eduerp.integrations.mail.MailClient#send}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mail")
package com.eduerp.integrations.mail;
