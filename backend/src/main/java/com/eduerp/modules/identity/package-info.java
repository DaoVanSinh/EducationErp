/**
 * Module Identity — nguồn sự thật duy nhất của tài khoản, thông tin đăng nhập và phiên (JWT trong
 * cookie + blacklist Redis). KHÔNG còn sở hữu role/group/permission (module {@code access}), chi
 * nhánh (module {@code organization}), audit log (module {@code audit}) hay số liệu dashboard
 * (module {@code dashboard}) — bị tách ra vì đó chính là "God Module" mà skill cấm (rule #13).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.modules.identity.IdentityManagement} (facade,
 *       cửa duy nhất cho module khác), constants, properties, exception, event.</li>
 *   <li>{@code dto} — hợp đồng vào/ra, có {@code @NamedInterface} nên module khác thấy được.</li>
 *   <li>{@code usecase} — mỗi class một use case, một method public {@code execute}.</li>
 *   <li>{@code web} — adapter HTTP: controller mỏng, cookie, filter, security filter chain.</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity")
package com.eduerp.modules.identity;
