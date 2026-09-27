package com.eduerp.identity.internal.repository;

import com.eduerp.identity.internal.model.Account;
import com.eduerp.identity.internal.util.EmailNormalizer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
