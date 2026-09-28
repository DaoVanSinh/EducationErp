package com.eduerp.modules.identity.internal.repository;

/** Kết quả thô của phép đếm tài khoản theo role; use case mới là nơi đổi nó thành DTO. */
public interface RoleHeadcountRow {

    String getRoleCode();

    String getRoleName();

    long getAccountCount();
}
