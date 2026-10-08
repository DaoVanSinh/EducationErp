/**
 * Module Enrollment — sở hữu việc ghi danh học viên vào một lớp và vòng đời của bản ghi ghi danh
 * (ACTIVE → WITHDRAWN/COMPLETED). Phụ thuộc một chiều {@code access} ({@code AccessConstants} cho
 * {@code @PreAuthorize}), {@code courses} ({@code CoursesManagement.getClassInfo} để chốt snapshot
 * course/branch/maxSeats) và {@code students} ({@code StudentsManagement.getProfile} để xác nhận
 * học viên còn hoạt động). KHÔNG biết tới {@code modules.billing} — billing đọc ngược qua
 * {@code EnrollmentManagement}, không có cạnh phụ thuộc ngược (spec mục 3).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@code EnrollmentManagement}, constants, exception, events.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Enrollment")
package com.eduerp.modules.enrollment;
