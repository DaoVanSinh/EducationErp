package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.internal.model.Course;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByCode(String code);

    Page<Course> findAll(Pageable pageable);
}
