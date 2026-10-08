/**
 * Module Access &amp; RBAC — sở hữu Role, Group, Permission, PermissionGroup, và bảng gán role/group
 * cho một account. Tự đủ dữ liệu để tính quyền hiệu lực: {@code account_id} chỉ là UUID trần có FK
 * mức DB tới {@code accounts.id}, không có quan hệ JPA sang entity {@code Account} của module
 * identity — nhờ vậy module này không bao giờ cần import {@code com.eduerp.modules.identity.*}.
 * Cùng lý do, module này cũng không import {@code com.eduerp.modules.organization.*}: danh sách chi
 * nhánh cho danh mục RBAC lấy qua {@link com.eduerp.modules.access.BranchCatalog} (cổng organization
 * implement) — nếu access import thẳng organization trong khi organization cũng cần
 * {@link com.eduerp.modules.access.AccessConstants} cho {@code @PreAuthorize} của riêng nó thì hai
 * module sẽ phụ thuộc vòng lẫn nhau, điều Spring Modulith không cho phép.
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.access.AccessManagement} (facade),
 *       constants, exception, event, {@link com.eduerp.modules.access.AccountExistenceCheck} (cổng
 *       identity implement để access xác nhận một account còn tồn tại mà không cần import identity),
 *       {@link com.eduerp.modules.access.BranchCatalog} (cổng organization implement, cùng lý do).</li>
 *   <li>{@code dto} — hợp đồng vào/ra, có {@code @NamedInterface} nên module khác thấy được.</li>
 *   <li>{@code usecase} — mỗi class một use case, một method public {@code execute}.</li>
 *   <li>{@code web} — adapter HTTP: controller mỏng.</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Access & RBAC")
package com.eduerp.modules.access;
