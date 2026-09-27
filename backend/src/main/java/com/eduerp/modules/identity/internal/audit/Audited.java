package com.eduerp.modules.identity.internal.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu một method sẽ được ghi audit sau khi chạy xong không lỗi. Mục đích là không phải rải
 * lệnh ghi log thủ công vào từng use case — chỗ nào quên là chỗ đó mất dấu vết.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    String action();

    String entityType();
}
