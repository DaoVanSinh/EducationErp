package com.eduerp.modules.enrollment;

import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module enrollment - type DUY NHẤT module khác được phép gọi (rule #1).
 * {@code modules.billing} gọi {@link #getEnrollment(UUID)} để chốt snapshot student/course/branch và
 * để chặn phát hành hoá đơn mới cho ghi danh không còn {@code ACTIVE} (spec mục 5).
 * {@code modules.academic} gọi {@link #listEnrollmentsByClass(UUID)} để dựng bảng điểm/bảng
 * tiến trình của một lớp (spec mục 3).
 */
@Service
public class EnrollmentManagement {

    public record EnrollmentSummaryResponse(UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
            EnrollmentConstants.EnrollmentStatus status) {
    }

    /**
     * Một dòng trong danh sách học viên của một lớp. {@code status}/{@code withdrawnAt} đi kèm vì
     * {@code modules.academic} dùng chính hai field này để đóng băng tiến trình của người đã rút
     * (spec mục 5) - lọc sẵn ở đây là tước mất khả năng đó của nơi gọi.
     */
    public record ClassRosterEntry(UUID enrollmentId, UUID studentProfileId, UUID courseId,
            EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt) {
    }

    private final EnrollmentRepository enrollments;

    EnrollmentManagement(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Transactional(readOnly = true)
    public Optional<EnrollmentSummaryResponse> getEnrollment(UUID enrollmentId) {
        return enrollments.findById(enrollmentId).map(enrollment -> new EnrollmentSummaryResponse(
                enrollment.getId(), enrollment.getStudentProfileId(), enrollment.getCourseId(),
                enrollment.getBranchId(), enrollment.getStatus()));
    }

    /**
     * Danh sách học viên của một lớp để {@code modules.academic} dựng bảng điểm/bảng tiến trình
     * (spec mục 3). CHỈ ĐỌC - spec nói rõ "Không có method ghi nào được thêm".
     *
     * <p>Trả về MỌI ghi danh của lớp, không chỉ {@code ACTIVE}: người đã rút vẫn phải có mặt trong
     * báo cáo với tiến trình đóng băng tại {@code withdrawnAt} (Review Focus #2). Nơi gọi phân biệt
     * bằng {@link ClassRosterEntry#status()}.
     */
    @Transactional(readOnly = true)
    public List<ClassRosterEntry> listEnrollmentsByClass(UUID classId) {
        return enrollments.findAllByClassIdOrderByEnrolledAtAsc(classId).stream()
                .map(enrollment -> new ClassRosterEntry(enrollment.getId(), enrollment.getStudentProfileId(),
                        enrollment.getCourseId(), enrollment.getStatus(), enrollment.getEnrolledAt(),
                        enrollment.getWithdrawnAt()))
                .toList();
    }
}
