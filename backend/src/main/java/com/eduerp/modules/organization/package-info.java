/**
 * Module Organization — sở hữu chi nhánh (Branch). Chỉ phụ thuộc {@code shared} và {@code access} —
 * vế sau vì hai lý do: dùng {@code AccessConstants.AccessRules} khi khai {@code @PreAuthorize} trên
 * {@link com.eduerp.modules.organization.web.BranchAdminController} (giống tiền lệ ở
 * {@code identity}), và implement {@link com.eduerp.modules.access.BranchCatalog} — cổng access tự
 * khai báo để lấy danh sách chi nhánh mà không phải import ngược organization (xem javadoc của
 * {@code access.BranchCatalog}). Chiều import vì vậy chỉ một chiều: organization → access.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.organization.OrganizationManagement}
 *       (facade, cửa duy nhất cho module khác).</li>
 *   <li>{@code dto} — hợp đồng vào/ra qua HTTP, {@code @NamedInterface("dto")}.</li>
 *   <li>{@code usecase} — một class = một use case (rule #7).</li>
 *   <li>{@code web} — controller mỏng (rule #8).</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác. Trong đó,
 *       {@code internal.access} là adapter phía organization cho {@code access.BranchCatalog}.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Organization")
package com.eduerp.modules.organization;
