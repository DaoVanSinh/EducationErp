package com.eduerp.modules.students.internal.repository;

import com.eduerp.modules.students.internal.model.StudentProfile;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    boolean existsByAccountId(UUID accountId);

    Optional<StudentProfile> findByAccountId(UUID accountId);

    Page<StudentProfile> findAll(Pageable pageable);

    /** Học viên của một chi nhánh = hồ sơ gắn với tài khoản thuộc chi nhánh đó (identity cấp danh sách id). */
    Page<StudentProfile> findByAccountIdIn(Collection<UUID> accountIds, Pageable pageable);
}
