package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    /**
     * Tra cứu theo email đúng như người dùng gõ vào. Cột email luôn lưu dạng đã chuẩn hoá, nên mọi
     * đầu vào từ ngoài phải đi qua đây — để một use case mới không lặp lại việc chuẩn hoá, hoặc quên.
     */
    default Optional<Account> findByTypedEmail(String typedEmail) {
        return findByEmail(EmailNormalizer.normalize(typedEmail));
    }

    long countByRole_Code(String roleCode);

    long countByHomeBranch_Id(UUID branchId);

    long countByStatus(IdentityConstants.AccountStatus status);

    /**
     * Đếm ở database chứ không tải hết Account về rồi group trong bộ nhớ — bảng này lớn dần theo
     * từng khoá tuyển sinh, còn dashboard thì được mở liên tục.
     */
    @Query("""
            SELECT r.code AS roleCode, r.name AS roleName, COUNT(a.id) AS accountCount
            FROM Role r LEFT JOIN Account a ON a.role = r
            GROUP BY r.code, r.name
            ORDER BY r.code
            """)
    List<RoleHeadcountRow> countAccountsByRole();
}
