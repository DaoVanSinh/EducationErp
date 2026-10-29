/**
 * Module Assessment — bài tập / mini-test định kỳ và mini-task tự ôn luyện của một lớp (đề cương ĐATN,
 * Phân hệ Quản lý Học tập: "theo dõi tiến độ làm bài tập/mini-test định kỳ của học viên ... Tích hợp các
 * mini-task giúp học viên tự ôn luyện trực tiếp trên hệ thống").
 *
 * <p>Hai khái niệm, một module vì cùng vòng đời "giáo viên giao bài cho lớp, theo dõi học viên làm bài":
 * <ul>
 *   <li>{@code Assignment} - bài tập/mini-test làm ngoài hệ thống; giáo viên ghi nhận tình trạng nộp và
 *       điểm của từng học viên, hệ thống tính tỷ lệ hoàn thành.</li>
 *   <li>{@code MiniTask} - bộ câu hỏi trắc nghiệm học viên làm TRỰC TIẾP trên hệ thống, chấm tự động,
 *       làm lại không giới hạn.</li>
 * </ul>
 * Tách khỏi {@code academic} (điểm tổng kết khóa) và {@code attendance} để không thành God Module.
 *
 * <p>Phụ thuộc một chiều: {@code access}, {@code courses} ({@code getClassInfo},
 * {@code classSummariesOf}), {@code enrollment} (danh sách lớp, ghi danh của học viên), {@code students}
 * (hồ sơ theo tài khoản, họ tên). {@code audit} chỉ lắng nghe {@code AssessmentEvents}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Assessment (Assignments & Mini-tasks)")
package com.eduerp.modules.assessment;
