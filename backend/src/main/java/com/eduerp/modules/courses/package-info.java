/**
 * Module Courses — sở hữu danh mục Khóa học và các Lớp học mở từ đó. Phụ thuộc một chiều
 * {@code shared}, {@code access} ({@code AccessConstants} cho {@code @PreAuthorize}),
 * {@code identity} (xác nhận {@code teacherId} là một account có thật qua
 * {@code IdentityManagement.summariesOf}) và {@code organization} (xác nhận {@code branchId} tồn tại
 * qua {@code OrganizationManagement.exists}, lấy tên chi nhánh qua {@code .namesOf}). Không module
 * nào đọc ngược từ {@code courses} ở phase này nên không có nguy cơ vòng phụ thuộc, không cần
 * port/adapter như {@code access.BranchCatalog}.
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
