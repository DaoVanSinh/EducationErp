/**
 * Module Identity &amp; Access — nguồn sự thật duy nhất của tài khoản, chi nhánh, role, group,
 * permission group và phiên đăng nhập (JWT trong cookie + blacklist Redis).
 *
 * <p>Vai trò từng package:
 * <ul>
 *   <li>base package — API của module: {@link com.eduerp.identity.IdentityManagement} (facade,
 *       cửa duy nhất cho module khác), constants, properties, exception, principal.</li>
 *   <li>{@code dto} — hợp đồng vào/ra, có {@code @NamedInterface} nên module khác thấy được.</li>
 *   <li>{@code usecase} — mỗi class một use case, một method public {@code execute}.</li>
 *   <li>{@code web} — adapter HTTP: controller mỏng, cookie, filter, security filter chain.</li>
 *   <li>{@code internal} — chi tiết cài đặt, Spring Modulith che khỏi mọi module khác.</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity & Access")
package com.eduerp.identity;
