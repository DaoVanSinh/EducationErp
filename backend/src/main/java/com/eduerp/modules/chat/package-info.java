/**
 * Module Chat — sở hữu LUẬT của việc hỏi chatbot tư vấn khóa học: chặn độ dài câu hỏi, chặn tần suất
 * theo tài khoản, dựng system prompt chống bịa đặt, và dịch mọi thất bại hạ tầng thành một lỗi nghiệp
 * vụ có errorCode.
 *
 * <p>KHÔNG sở hữu dữ liệu nào. Không entity, không repository, không bảng DB - "mỗi phiên chat chỉ
 * tồn tại trong bộ nhớ trình duyệt" (spec mục 2). Lịch sử hội thoại đi vào qua
 * {@code AskChatbotRequest.history} và biến mất ngay sau khi response được trả. Vì không lưu gì nên
 * cũng KHÔNG có {@code ChatEvents} và KHÔNG ghi audit: một dòng audit truy ngược được về câu hỏi của
 * học viên chính là lưu hội thoại bằng cửa sau, trái đúng lý do spec đã nêu (dữ liệu học viên nhạy
 * cảm, có thể là trẻ vị thành niên).
 *
 * <p>Phụ thuộc một chiều: {@code access} ({@code AccessConstants.AccessRules.CREATE_CHAT} cho
 * {@code @PreAuthorize}), {@code courses} ({@code CoursesManagement.searchOpenCourses} để truy xuất
 * khóa học ĐANG MỞ), {@code integrations.ai} ({@code AiChatClient} để sinh câu trả lời),
 * {@code integrations.cache} ({@code CacheKeyBuilder} để lắp key rate limit), {@code shared}
 * ({@code AccountPrincipal}). KHÔNG module nào đọc ngược từ {@code chat}, nên không có facade
 * {@code ChatManagement} - mirror cách {@code modules.academic} không có facade (rule #1: chỉ tạo
 * facade khi có nơi gọi thật).
 *
 * <p>KHÔNG có guard kiểm sở hữu như {@code ClassGradeAccessGuard} của Phân hệ 4: ở đây không có khái
 * niệm "đúng lớp của ai" - ai có quyền {@code CHAT:CREATE} ở bất kỳ scope nào cũng được hỏi
 * (spec mục 3.4).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: constants, exception.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Chat (AI Course Advisor)")
package com.eduerp.modules.chat;
