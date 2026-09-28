/**
 * Module Organization — sở hữu chi nhánh (Branch). Module lá: không phụ thuộc module nghiệp vụ nào
 * khác, chỉ {@code shared}.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.organization.OrganizationManagement}
 *       (facade, cửa duy nhất cho module khác).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization")
package com.eduerp.modules.organization;
