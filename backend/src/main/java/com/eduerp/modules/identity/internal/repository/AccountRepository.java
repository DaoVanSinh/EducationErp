package com.eduerp.modules.identity.internal.repository;

import com.eduerp.modules.identity.IdentityConstants;
import com.eduerp.modules.identity.internal.model.Account;
import com.eduerp.modules.identity.internal.util.EmailNormalizer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    /**
     * Tra cứu theo email đúng như người dùng gõ vào. Cột email luôn lưu dạng đã chuẩn hoá, nên mọi
     * đầu vào từ ngoài phải đi qua đây — để một use case mới không lặp lại việc chuẩn hoá, hoặc quên.
     */
    default Optional<Account> findByTypedEmail(String typedEmail) {
        return findByEmail(EmailNormalizer.normalize(typedEmail));
    }

    long countByStatus(IdentityConstants.AccountStatus status);

    long countByHomeBranchId(UUID branchId);

    long countByHomeBranchIdAndStatus(UUID branchId, IdentityConstants.AccountStatus status);

    Page<Account> findAllByHomeBranchId(UUID branchId, Pageable pageable);

    @Query("SELECT a.id FROM Account a WHERE a.homeBranchId = :branchId")
    List<UUID> findIdsByHomeBranchId(@Param("branchId") UUID branchId);
}
