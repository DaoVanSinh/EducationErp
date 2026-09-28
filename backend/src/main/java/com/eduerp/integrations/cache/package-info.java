/**
 * Redis: nơi duy nhất một chuỗi key được lắp. Key nối bằng string rải rác trong các module khiến
 * việc invalidate đúng trở thành bất khả thi, vì không gì liệt kê được đang có những key nào.
 *
 * <p>Package này không biết bất kỳ nghiệp vụ nào: mỗi module tự cấp namespace của mình từ
 * constants riêng, {@code CacheKeyBuilder} chỉ lắp lại thành key.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Cache")
package com.eduerp.integrations.cache;
