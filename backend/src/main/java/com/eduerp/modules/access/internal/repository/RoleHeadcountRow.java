package com.eduerp.modules.access.internal.repository;

/** Kết quả thô của phép đếm account theo role; facade là nơi đổi nó thành DTO cho module gọi. */
public interface RoleHeadcountRow {

    String getRoleCode();

    String getRoleName();

    long getAccountCount();
}
