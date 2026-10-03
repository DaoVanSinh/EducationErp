package com.eduerp.modules.students.internal.repository;

import com.eduerp.modules.students.internal.model.StudentProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    boolean existsByAccountId(UUID accountId);

    Optional<StudentProfile> findByAccountId(UUID accountId);

    Page<StudentProfile> findAll(Pageable pageable);
}
