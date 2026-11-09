package com.eduerp.modules.courses.internal;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.TeacherEligibilityPort;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Chốt người được phân công dạy lớp cho cả mở lớp lẫn thay giáo viên: phải có năng lực giảng dạy, còn làm việc, đã có
 * lương, cùng chi nhánh với lớp và không trùng giờ lớp khác đang mở.
 *
 * <p>"Năng lực giảng dạy" là cổng {@code TEACHING:READ}, không phải đúng mã vai trò {@code TEACHER}: vai trò tự tạo
 * cũng được phân công nếu có cổng này (rbac.md: permissions over roles).
 */
@Component
public class TeacherAssignmentGuard {

    private static final String FIELD = "teacherId";
    private static final List<CoursesConstants.ClassStatus> OPEN_STATUSES = Arrays.stream(
            CoursesConstants.ClassStatus.values()).filter(CoursesConstants.ClassStatus::isOpen).toList();

    private final IdentityManagement identity;
    private final AccessManagement access;
    private final TeacherEligibilityPort eligibility;
    private final ClassRepository classes;

    TeacherAssignmentGuard(IdentityManagement identity, AccessManagement access, TeacherEligibilityPort eligibility,
            ClassRepository classes) {
        this.identity = identity;
        this.access = access;
        this.eligibility = eligibility;
        this.classes = classes;
    }

    /** Lịch và thời gian của lớp sắp nhận giáo viên. {@code excludedClassId} null khi mở lớp mới. */
    public record ClassPlan(UUID branchId, List<WeeklyScheduleSlot> schedule, LocalDate startDate, LocalDate endDate,
            UUID excludedClassId) {
    }

    public void requireAssignable(UUID teacherId, ClassPlan plan) {
        requireTeacher(teacherId);
        eligibility.assignmentProblem(teacherId).ifPresent(problem -> {
            throw new AppValidationException(FIELD, problem);
        });
        if (!plan.branchId().equals(identity.homeBranchOf(teacherId).orElse(null))) {
            throw new AppValidationException(FIELD, "Giáo viên chỉ dạy lớp thuộc chi nhánh chủ quản của mình");
        }
        classes.findAllByTeacherIdAndStatusIn(teacherId, OPEN_STATUSES).stream()
                .filter(other -> !other.getId().equals(plan.excludedClassId()))
                .filter(other -> overlaps(other, plan))
                .findFirst()
                .ifPresent(other -> {
                    throw new AppValidationException(FIELD, "Giáo viên đang dạy lớp " + other.getCode() + " trùng giờ");
                });
    }

    private void requireTeacher(UUID teacherId) {
        if (!identity.summariesOf(List.of(teacherId)).containsKey(teacherId)) {
            throw new AppValidationException(FIELD, "Giáo viên không tồn tại");
        }
        if (!access.grants(teacherId, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ)) {
            throw new AppValidationException(FIELD, "Tài khoản được chọn không có năng lực giảng dạy");
        }
    }

    private static boolean overlaps(Class other, ClassPlan plan) {
        return datesOverlap(other.getStartDate(), other.getEndDate(), plan.startDate(), plan.endDate())
                && other.getSchedule().stream().anyMatch(slot -> plan.schedule().stream().anyMatch(
                        wanted -> wanted.dayOfWeek() == slot.getDayOfWeek()
                                && wanted.startTime().isBefore(slot.getEndTime())
                                && slot.getStartTime().isBefore(wanted.endTime())));
    }

    /** Ngày kết thúc null là chưa chốt, coi như còn kéo dài. */
    private static boolean datesOverlap(LocalDate startA, LocalDate endA, LocalDate startB, LocalDate endB) {
        return (endB == null || !startA.isAfter(endB)) && (endA == null || !startB.isAfter(endA));
    }
}
