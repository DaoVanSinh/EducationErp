package com.eduerp.modules.access.internal.repository;

import com.eduerp.modules.access.internal.model.AccountRoleAssignment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, UUID> {

    long countByRole_Code(String roleCode);

    /**
     * Đếm ở database chứ không tải hết account về rồi group trong bộ nhớ — bảng này lớn dần theo
     * từng khoá tuyển sinh, còn dashboard thì được mở liên tục. LEFT JOIN từ {@code Role} chứ không
     * {@code AccountRoleAssignment}: một role chưa ai giữ vẫn phải hiện ra với 0, không được biến
     * mất khỏi thống kê.
     */
    @Query("""
            SELECT r.code AS roleCode, r.name AS roleName, COUNT(a.accountId) AS accountCount
            FROM Role r LEFT JOIN AccountRoleAssignment a ON a.role = r
            GROUP BY r.code, r.name
            ORDER BY r.code
            """)
    List<RoleHeadcountRow> countAccountsByRole();

    /**
     * Bản lọc theo chi nhánh của {@link #countAccountsByRole()} — bộ lọc id account phải nằm trong
     * mệnh đề {@code ON} của LEFT JOIN, không phải {@code WHERE}: đặt ở WHERE sẽ loại luôn những dòng
     * NULL do LEFT JOIN sinh ra, tức một role không ai ở chi nhánh này giữ sẽ biến mất thay vì hiện 0.
     */
    @Query("""
            SELECT r.code AS roleCode, r.name AS roleName, COUNT(a.accountId) AS accountCount
            FROM Role r LEFT JOIN AccountRoleAssignment a ON a.role = r AND a.accountId IN :accountIds
            GROUP BY r.code, r.name
            ORDER BY r.code
            """)
    List<RoleHeadcountRow> countAccountsByRoleForAccounts(@Param("accountIds") Collection<UUID> accountIds);
}
