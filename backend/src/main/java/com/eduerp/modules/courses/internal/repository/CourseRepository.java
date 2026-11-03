package com.eduerp.modules.courses.internal.repository;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.internal.model.Course;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, UUID> {
    Optional<Course> findByCode(String code);

    Page<Course> findAll(Pageable pageable);

    /** Khoá có ít nhất một lớp ở chi nhánh, không tính lớp ở trạng thái {@code excluded}. */
    @Query(value = """
            SELECT course FROM Course course WHERE EXISTS (
                SELECT 1 FROM Class cls
                WHERE cls.course = course AND cls.branchId = :branchId AND cls.status <> :excluded)
            """, countQuery = """
            SELECT COUNT(course) FROM Course course WHERE EXISTS (
                SELECT 1 FROM Class cls
                WHERE cls.course = course AND cls.branchId = :branchId AND cls.status <> :excluded)
            """)
    Page<Course> findAllWithClassAtBranch(@Param("branchId") UUID branchId,
            @Param("excluded") CoursesConstants.ClassStatus excluded, Pageable pageable);

    List<Course> findAllByActiveTrueOrderByCodeAsc();
}
