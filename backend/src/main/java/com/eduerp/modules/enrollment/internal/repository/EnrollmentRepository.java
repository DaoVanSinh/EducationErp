package com.eduerp.modules.enrollment.internal.repository;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import java.util.List;
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
     * Toàn bộ ghi danh của một lớp, kể cả WITHDRAWN/COMPLETED - {@code modules.academic} cần cả
     * những dòng đó để đóng băng tiến trình tại {@code withdrawnAt} (spec mục 5). Sắp theo
     * {@code enrolledAt} để bảng điểm/bảng tiến trình có thứ tự ổn định giữa hai lần tải.
     */
    List<Enrollment> findAllByClassIdOrderByEnrolledAtAsc(UUID classId);

    /**
     * Ba filter đều optional (spec mục 4 {@code ListEnrollments} + spec 3.1 mục 7). Một query với
     * {@code :p IS NULL} thay vì tám method {@code findAllBy...} - giữ usecase ở complexity 1.
     * {@code EnrollmentRepositoryIT.searchFiltersByEveryCombination...} và
     * {@code ...searchFiltersByOptionalStatus} phủ mọi tổ hợp nên nếu Hibernate/Postgres không suy
     * được kiểu tham số null thì test đỏ ngay, không lọt ra production.
     */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentProfileId IS NULL OR e.studentProfileId = :studentProfileId)
              AND (:classId IS NULL OR e.classId = :classId)
              AND (:status IS NULL OR e.status = :status)
            """)
    Page<Enrollment> search(@Param("studentProfileId") UUID studentProfileId, @Param("classId") UUID classId,
            @Param("status") EnrollmentConstants.EnrollmentStatus status, Pageable pageable);
}
