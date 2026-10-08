package com.eduerp.modules.teachers.internal.repository;

import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeacherProfileRepository extends JpaRepository<TeacherProfile, UUID> {
    boolean existsByAccountId(UUID accountId);

    Optional<TeacherProfile> findByAccountId(UUID accountId);

    Page<TeacherProfile> findAll(Pageable pageable);
}
