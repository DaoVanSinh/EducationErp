/**
 * Module Courses — sở hữu danh mục Khóa học và các Lớp học mở từ đó. Phụ thuộc một chiều
 * {@code shared}, {@code access} ({@code AccessConstants} cho {@code @PreAuthorize}),
 * {@code identity} (xác nhận {@code teacherId} là một account có thật qua
 * {@code IdentityManagement.summariesOf}) và {@code organization} (xác nhận {@code branchId} tồn tại
 * qua {@code OrganizationManagement.exists}, lấy tên chi nhánh qua {@code .namesOf}). Không module
 * nào đọc ngược từ {@code courses} ở phase này nên không có nguy cơ vòng phụ thuộc, không cần
 * port/adapter như {@code access.BranchCatalog}.
 *
 * <p>Người đọc mới ở Phân hệ 5: {@code modules.chat} gọi
 * {@code CoursesManagement.searchOpenCourses} để truy xuất khóa học ĐANG MỞ cho chatbot, và chỉ thấy
 * {@code OpenCourseSummary} - không module nào bên ngoài biết bảng {@code courses} có cột
 * {@code search_vector} (V26) hay rằng việc tìm kiếm là full-text search của Postgres.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.courses.CoursesManagement}.</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Courses & Classes")
package com.eduerp.modules.courses;
