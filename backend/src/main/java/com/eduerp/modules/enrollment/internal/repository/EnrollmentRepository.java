package com.eduerp.modules.enrollment.internal.repository;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import java.util.Collection;
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

    boolean existsByStudentProfileIdAndStatus(UUID studentProfileId, EnrollmentConstants.EnrollmentStatus status);

    List<Enrollment> findAllByClassIdAndStatus(UUID classId, EnrollmentConstants.EnrollmentStatus status);

    /** Sĩ số đang học của nhiều lớp trong MỘT truy vấn - cho danh sách lớp và tác vụ tự khai giảng. */
    @Query("""
            SELECT e.classId AS classId, COUNT(e) AS total FROM Enrollment e
            WHERE e.classId IN :classIds AND e.status = :status
            GROUP BY e.classId
            """)
    List<ClassCountRow> countByClassIdsAndStatus(@Param("classIds") Collection<UUID> classIds,
            @Param("status") EnrollmentConstants.EnrollmentStatus status);

    /**
     * Toàn bộ ghi danh của một lớp, kể cả WITHDRAWN/COMPLETED - {@code modules.academic} cần cả
     * những dòng đó để đóng băng tiến trình tại {@code withdrawnAt} (spec mục 5). Sắp theo
     * {@code enrolledAt} để bảng điểm/bảng tiến trình có thứ tự ổn định giữa hai lần tải.
     */
    List<Enrollment> findAllByClassIdOrderByEnrolledAtAsc(UUID classId);

    /**
     * Bốn filter đều optional (spec mục 4 {@code ListEnrollments} + spec 3.1 mục 7, thêm {@code branchId}
     * cho lớp 2 của RBAC). Một query với {@code :p IS NULL} thay vì mười sáu method {@code findAllBy...} -
     * giữ usecase ở complexity 1. {@code EnrollmentRepositoryIT} phủ các tổ hợp nên nếu Hibernate/Postgres
     * không suy được kiểu tham số null thì test đỏ ngay, không lọt ra production.
     */
    @Query("""
            SELECT e FROM Enrollment e
            WHERE (:studentProfileId IS NULL OR e.studentProfileId = :studentProfileId)
              AND (:classId IS NULL OR e.classId = :classId)
              AND (:status IS NULL OR e.status = :status)
              AND (:branchId IS NULL OR e.branchId = :branchId)
            """)
    Page<Enrollment> search(@Param("studentProfileId") UUID studentProfileId, @Param("classId") UUID classId,
            @Param("status") EnrollmentConstants.EnrollmentStatus status, @Param("branchId") UUID branchId,
            Pageable pageable);

    interface ClassCountRow {
        UUID getClassId();

        long getTotal();
    }
}
