package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Class;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<Class, UUID> {
    Optional<Class> findByCode(String code);

    Page<Class> findAll(Pageable pageable);
}
