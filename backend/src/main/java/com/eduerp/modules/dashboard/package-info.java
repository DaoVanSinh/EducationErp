/**
 * Module Dashboard — đọc-tổng-hợp thuần tuý, không sở hữu bảng nào của riêng nó. Compose dữ liệu từ
 * facade của {@code identity} (số tài khoản, hồ sơ), {@code access} (số theo role) và {@code audit}
 * (hoạt động gần đây) ngay tại use case (rule #3) — không JOIN chéo bảng của module khác.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Dashboard")
package com.eduerp.modules.dashboard;
