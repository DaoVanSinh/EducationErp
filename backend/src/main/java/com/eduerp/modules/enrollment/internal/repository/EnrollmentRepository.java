package com.eduerp.modules.enrollment.internal.repository;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    long countByClassIdAndStatus(UUID classId, EnrollmentConstants.EnrollmentStatus status);

    boolean existsByStudentProfileIdAndClassIdAndStatus(UUID studentProfileId, UUID classId,
            EnrollmentConstants.EnrollmentStatus status);

    /**
     * Hai filter đều optional (spec mục 4 {@code ListEnrollments}). Một query với {@code :p IS NULL}
     * thay vì bốn method {@code findAllBy...} - giữ usecase ở complexity 1 thay vì rẽ 4 nhánh.
     * {@code EnrollmentRepositoryIT.searchFiltersByEveryCombination...} phủ cả 4 tổ hợp nên nếu
     * Hibernate/Postgres không suy được kiểu tham số null thì test đỏ ngay, không lọt ra production.
     */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentProfileId IS NULL OR e.studentProfileId = :studentProfileId)
              AND (:classId IS NULL OR e.classId = :classId)
            """)
    Page<Enrollment> search(@Param("studentProfileId") UUID studentProfileId, @Param("classId") UUID classId,
            Pageable pageable);
}
