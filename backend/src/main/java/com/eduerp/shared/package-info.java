/**
 * Module dùng chung duy nhất được phép tồn tại (rule #16), chỉ chứa khái niệm đã chứng minh cần cho
 * 3+ module và ổn định — không phải nơi để đó "phòng khi sau này dùng chung". Một chiều: mọi module
 * nghiệp vụ được phụ thuộc vào đây, nhưng {@code shared} không bao giờ import ngược lại một module
 * nghiệp vụ nào.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Shared")
package com.eduerp.shared;
