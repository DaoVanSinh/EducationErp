/**
 * Adapter theo vòng đời ứng dụng: {@code ApplicationRunner} kích hoạt use case khi khởi động,
 * đóng vai trò giống {@code web} nhưng nguồn kích hoạt là container thay vì HTTP.
 * Mỗi runner chỉ được gọi đúng một use case và không chứa quyết định nghiệp vụ nào.
 */
package com.eduerp.modules.identity.startup;
