package com.eduerp.modules.courses.internal.rules;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.courses.ClassLifecycleException;
import com.eduerp.modules.courses.CoursesConstants;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Luật vòng đời lớp - thuần, không I/O, test được bằng {@code new}. Use case nạp dữ liệu (sĩ số đang học,
 * hôm nay) rồi hỏi ở đây; ném lỗi nghĩa là "không được", không ném là được.
 */
public final class ClassLifecycleRules {

    private ClassLifecycleRules() {
    }

    /**
     * Điều kiện khai giảng: đang tuyển sinh, đã tới ngày khai giảng, có lịch học, đủ sĩ số tối thiểu. Thứ tự
     * kiểm tra là thứ tự người dùng cần sửa: sai trạng thái thì không gì khác có nghĩa.
     */
    public static void requireStartable(UUID classId, CoursesConstants.ClassStatus status, LocalDate startDate,
            LocalDate today, boolean hasSchedule, long activeEnrollments, int minSeats) {
        if (status != CoursesConstants.ClassStatus.ENROLLING) {
            throw new ClassLifecycleException.InvalidTransition(classId, status,
                    CoursesConstants.ClassStatus.IN_PROGRESS);
        }
        if (today.isBefore(startDate)) {
            throw new ClassLifecycleException.StartDateNotReached(startDate);
        }
        if (!hasSchedule) {
            throw new ClassLifecycleException.NoSchedule();
        }
        if (activeEnrollments < minSeats) {
            throw new ClassLifecycleException.NotEnoughStudents(activeEnrollments, minSeats);
        }
    }

    /** Cùng điều kiện với {@link #requireStartable}, dạng boolean cho tác vụ tự khai giảng (không ném). */
    public static boolean isStartable(CoursesConstants.ClassStatus status, LocalDate startDate, LocalDate today,
            boolean hasSchedule, long activeEnrollments, int minSeats) {
        return status == CoursesConstants.ClassStatus.ENROLLING && !today.isBefore(startDate) && hasSchedule
                && activeEnrollments >= minSeats;
    }

    public static void requireCompletable(UUID classId, CoursesConstants.ClassStatus status) {
        if (status != CoursesConstants.ClassStatus.IN_PROGRESS) {
            throw new ClassLifecycleException.InvalidTransition(classId, status,
                    CoursesConstants.ClassStatus.COMPLETED);
        }
    }

    /** Chỉ huỷ được lớp chưa khai giảng; lớp đang học thì phải kết thúc (có điểm, có điểm danh). */
    public static void requireCancellable(UUID classId, CoursesConstants.ClassStatus status) {
        if (status != CoursesConstants.ClassStatus.ENROLLING) {
            throw new ClassLifecycleException.InvalidTransition(classId, status,
                    CoursesConstants.ClassStatus.CANCELLED);
        }
    }

    public static void requireEditable(UUID classId, CoursesConstants.ClassStatus status) {
        if (!status.isOpen()) {
            throw new ClassLifecycleException.NotEditable(classId, status);
        }
    }

    /** Sĩ số tối thiểu không vượt tối đa; ngày kết thúc dự kiến (nếu có) không trước ngày khai giảng. */
    public static void requireValidPlan(int minSeats, int maxSeats, LocalDate startDate, LocalDate endDate) {
        if (minSeats > maxSeats) {
            throw new AppValidationException("minSeats", "Sĩ số tối thiểu không được lớn hơn sĩ số tối đa");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new AppValidationException("endDate", "Ngày kết thúc dự kiến không được trước ngày khai giảng");
        }
    }

    public static void requireStartDateNotPast(LocalDate startDate, LocalDate today) {
        if (startDate.isBefore(today)) {
            throw new AppValidationException("startDate", "Ngày khai giảng không được ở quá khứ");
        }
    }

    public static void requireSeatsCoverEnrolled(int maxSeats, long activeEnrollments) {
        if (maxSeats < activeEnrollments) {
            throw new ClassLifecycleException.SeatsBelowEnrolled(maxSeats, activeEnrollments);
        }
    }
}
