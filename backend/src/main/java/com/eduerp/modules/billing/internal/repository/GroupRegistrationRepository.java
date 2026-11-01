package com.eduerp.modules.billing.internal.repository;

import com.eduerp.modules.billing.internal.model.GroupRegistration;
import com.eduerp.modules.billing.internal.model.GroupRegistrationMember;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRegistrationRepository extends JpaRepository<GroupRegistration, UUID> {

    /** Filter optional dồn vào một query (mirror {@code ComboRepository.search}) - use case không rẽ nhánh. */
    @Query("""
            SELECT g FROM GroupRegistration g
            WHERE (:courseId IS NULL OR g.courseId = :courseId)
              AND (:branchId IS NULL OR g.branchId = :branchId)
            """)
    Page<GroupRegistration> search(@Param("courseId") UUID courseId, @Param("branchId") UUID branchId,
            Pageable pageable);

    /** Thành viên ứng với một ghi danh - {@code CreateInvoice} đọc trần học phí đã giảm từ đây. */
    @Query("SELECT m FROM GroupRegistrationMember m WHERE m.enrollmentId = :enrollmentId")
    Optional<GroupRegistrationMember> findMemberByEnrollmentId(@Param("enrollmentId") UUID enrollmentId);
}
