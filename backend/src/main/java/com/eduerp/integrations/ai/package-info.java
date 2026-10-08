/**
 * Một nhà cung cấp mô hình ngôn ngữ, sau một {@code @Service} mỏng. Cơ chế thuần: không biết khóa
 * học, học viên hay trung tâm là gì - module nghiệp vụ ({@code modules.chat}) tự dựng toàn bộ nội
 * dung system message (luật chống bịa đặt + danh sách khóa học truy xuất được) rồi truyền vào.
 *
 * <p>Đơn giản hơn {@code integrations.payment} ĐÚNG MỘT BẬC, và đó là chủ đích (spec Ruling R2):
 * {@code ChatClient} của Spring AI ĐÃ TỰ là lớp trừu tượng đa nhà cung cấp - đổi Anthropic sang
 * OpenAI/Ollama chỉ là đổi dependency + cấu hình, không đổi một dòng code gọi - nên ở đây KHÔNG có
 * {@code interface} + nhiều {@code @Component} như VNPay/MoMo. Chỉ cần một lớp bọc để
 * {@code modules.chat} không bao giờ import type của Spring AI, mirror cách
 * {@code modules.billing} không bao giờ import SDK của VNPay/MoMo.
 *
 * <p>KHÔNG phụ thuộc bất kỳ {@code modules.*} nào, và KHÔNG lưu gì: lịch sử hội thoại đi vào qua
 * tham số và đi ra khỏi bộ nhớ ngay sau lệnh gọi (spec mục 2 - không lưu hội thoại ở backend).
 */
@org.springframework.modulith.ApplicationModule(displayName = "AI Chat Provider")
package com.eduerp.integrations.ai;
