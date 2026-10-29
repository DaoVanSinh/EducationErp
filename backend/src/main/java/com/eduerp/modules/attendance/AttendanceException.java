package com.eduerp.modules.attendance;

import com.eduerp.core.exception.AppException;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module attendance phát ra - mirror {@code AcademicException}. */
public sealed class AttendanceException extends AppException
        permits AttendanceException.ClassNotFound, AttendanceException.NotAssignedToClass,
        AttendanceException.EnrollmentNotInClass, AttendanceException.FutureSessionDate,
        AttendanceException.DuplicateEntry, AttendanceException.ClassNotInProgress,
        AttendanceException.BeforeClassStart {

    protected AttendanceException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    public static final class ClassNotFound extends AttendanceException {
        public ClassNotFound(UUID classId) {
            super("ATTENDANCE_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
        }
    }

    /** Có quyền ATTENDANCE ở scope hẹp nhưng không phải giáo viên được phân công dạy lớp này. 403 chứ
     * không 404 - mirror {@code NotAssignedToClassException} của academic. */
    public static final class NotAssignedToClass extends AttendanceException {
        public NotAssignedToClass(UUID actorAccountId, UUID classId) {
            super("ATTENDANCE_NOT_ASSIGNED_TO_CLASS", HttpStatus.FORBIDDEN,
                    "Tài khoản " + actorAccountId + " không được phân công dạy lớp " + classId);
        }
    }

    public static final class EnrollmentNotInClass extends AttendanceException {
        public EnrollmentNotInClass(UUID enrollmentId, UUID classId) {
            super("ATTENDANCE_ENROLLMENT_NOT_IN_CLASS", HttpStatus.BAD_REQUEST,
                    "Lượt ghi danh " + enrollmentId + " không thuộc lớp " + classId);
        }
    }

    public static final class FutureSessionDate extends AttendanceException {
        public FutureSessionDate(LocalDate sessionDate) {
            super("ATTENDANCE_FUTURE_SESSION_DATE", HttpStatus.BAD_REQUEST,
                    "Không thể điểm danh cho ngày " + sessionDate + " vì chưa tới");
        }
    }

    /** Lớp chưa khai giảng, đã kết thúc hoặc đã huỷ - không có buổi học nào để điểm danh. */
    public static final class ClassNotInProgress extends AttendanceException {
        public ClassNotInProgress(UUID classId) {
            super("ATTENDANCE_CLASS_NOT_IN_PROGRESS", HttpStatus.CONFLICT,
                    "Lớp " + classId + " không ở trạng thái đang học, không điểm danh được");
        }
    }

    public static final class BeforeClassStart extends AttendanceException {
        public BeforeClassStart(LocalDate sessionDate, LocalDate startDate) {
            super("ATTENDANCE_BEFORE_CLASS_START", HttpStatus.BAD_REQUEST,
                    "Ngày " + sessionDate + " trước ngày khai giảng " + startDate + " của lớp");
        }
    }

    public static final class DuplicateEntry extends AttendanceException {
        public DuplicateEntry(UUID enrollmentId) {
            super("ATTENDANCE_DUPLICATE_ENTRY", HttpStatus.BAD_REQUEST,
                    "Học viên của lượt ghi danh " + enrollmentId + " xuất hiện hai lần trong một buổi điểm danh");
        }
    }
}
