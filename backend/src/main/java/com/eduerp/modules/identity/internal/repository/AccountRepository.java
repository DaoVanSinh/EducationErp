package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findFirstByEmailAndStatusNot(String email, IdentityConstants.AccountStatus status);

    /**
     * Tra cứu theo email đúng như người dùng gõ vào, bỏ qua tài khoản đã đóng: một email chỉ có một tài khoản chưa
     * đóng, còn tài khoản đã đóng không đăng nhập được và không giữ chỗ email. Cột email luôn lưu dạng đã chuẩn hoá,
     * nên mọi đầu vào từ ngoài phải đi qua đây.
     */
    default Optional<Account> findByTypedEmail(String typedEmail) {
        return findFirstByEmailAndStatusNot(EmailNormalizer.normalize(typedEmail),
                IdentityConstants.AccountStatus.CLOSED);
    }

    long countByStatus(IdentityConstants.AccountStatus status);

    long countByHomeBranchId(UUID branchId);

    long countByHomeBranchIdAndStatus(UUID branchId, IdentityConstants.AccountStatus status);

    /**
     * Danh sách tài khoản có bộ lọc. {@code pattern} luôn có giá trị, không lọc thì là "%". {@code accountIds} chỉ
     * có tác dụng khi {@code filterByIds} bật, để lọc theo vai trò mà không JOIN sang bảng của module access.
     */
    @Query("""
            SELECT a FROM Account a
            WHERE (:branchId IS NULL OR a.homeBranchId = :branchId)
              AND (:status IS NULL OR a.status = :status)
              AND (LOWER(a.email) LIKE :pattern OR LOWER(a.fullName) LIKE :pattern)
              AND (:filterByIds = FALSE OR a.id IN :accountIds)
            """)
    Page<Account> search(@Param("branchId") UUID branchId, @Param("status") IdentityConstants.AccountStatus status,
            @Param("pattern") String pattern, @Param("filterByIds") boolean filterByIds,
            @Param("accountIds") Collection<UUID> accountIds, Pageable pageable);

    @Query("SELECT a.id FROM Account a WHERE LOWER(a.email) LIKE :pattern OR LOWER(a.fullName) LIKE :pattern")
    List<UUID> findIdsMatching(@Param("pattern") String pattern);

    @Query("SELECT a.id FROM Account a WHERE a.homeBranchId = :branchId")
    List<UUID> findIdsByHomeBranchId(@Param("branchId") UUID branchId);
}
