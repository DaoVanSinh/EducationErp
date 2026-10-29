package com.eduerp.modules.courses;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Lỗi vòng đời lớp học - 409 (xung đột với trạng thái hiện tại của lớp), mỗi loại một {@code errorCode} riêng
 * để giao diện giải thích đúng lý do thay vì một câu "không thực hiện được" chung chung.
 */
public sealed class ClassLifecycleException extends CoursesException
        permits ClassLifecycleException.NotEditable, ClassLifecycleException.InvalidTransition,
        ClassLifecycleException.NotEnoughStudents, ClassLifecycleException.StartDateNotReached,
        ClassLifecycleException.NoSchedule, ClassLifecycleException.SeatsBelowEnrolled,
        ClassLifecycleException.TeacherHasOpenClasses {

    protected ClassLifecycleException(String errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }

    public static final class NotEditable extends ClassLifecycleException {
        public NotEditable(UUID classId, CoursesConstants.ClassStatus status) {
            super(CoursesConstants.ErrorCodes.CLASS_NOT_EDITABLE,
                    "Lớp " + classId + " đang ở trạng thái " + status + ", không sửa được nữa");
        }
    }

    public static final class InvalidTransition extends ClassLifecycleException {
        public InvalidTransition(UUID classId, CoursesConstants.ClassStatus from, CoursesConstants.ClassStatus to) {
            super(CoursesConstants.ErrorCodes.CLASS_INVALID_TRANSITION,
                    "Lớp " + classId + " không chuyển được từ " + from + " sang " + to);
        }
    }

    public static final class NotEnoughStudents extends ClassLifecycleException {
        public NotEnoughStudents(long enrolled, int minSeats) {
            super(CoursesConstants.ErrorCodes.CLASS_NOT_ENOUGH_STUDENTS,
                    "Lớp mới có " + enrolled + " học viên, cần tối thiểu " + minSeats + " để khai giảng");
        }
    }

    public static final class StartDateNotReached extends ClassLifecycleException {
        public StartDateNotReached(LocalDate startDate) {
            super(CoursesConstants.ErrorCodes.CLASS_START_DATE_NOT_REACHED,
                    "Chưa tới ngày khai giảng " + startDate + " - sửa ngày khai giảng nếu muốn học sớm hơn");
        }
    }

    public static final class NoSchedule extends ClassLifecycleException {
        public NoSchedule() {
            super(CoursesConstants.ErrorCodes.CLASS_NO_SCHEDULE, "Lớp chưa có lịch học hàng tuần, chưa khai giảng được");
        }
    }

    public static final class SeatsBelowEnrolled extends ClassLifecycleException {
        public SeatsBelowEnrolled(int maxSeats, long enrolled) {
            super(CoursesConstants.ErrorCodes.CLASS_SEATS_BELOW_ENROLLED,
                    "Sĩ số tối đa " + maxSeats + " nhỏ hơn số học viên đang học " + enrolled);
        }
    }

    /** Đổi vai trò làm mất năng lực giáo viên khi người đó còn được phân công dạy lớp đang mở. */
    public static final class TeacherHasOpenClasses extends ClassLifecycleException {
        public TeacherHasOpenClasses(UUID teacherId, long openClasses) {
            super(CoursesConstants.ErrorCodes.TEACHER_HAS_OPEN_CLASSES,
                    "Tài khoản " + teacherId + " còn dạy " + openClasses + " lớp đang mở - hãy đổi giáo viên của các lớp đó trước");
        }
    }
}
