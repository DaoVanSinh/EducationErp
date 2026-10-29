package com.eduerp.modules.assessment;

import com.eduerp.core.exception.AppException;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module assessment phát ra. */
public sealed class AssessmentException extends AppException
        permits AssessmentException.ClassNotFound, AssessmentException.NotAssignedToClass,
        AssessmentException.AssignmentNotFound, AssessmentException.MiniTaskNotFound,
        AssessmentException.EnrollmentNotInClass, AssessmentException.DuplicateEntry,
        AssessmentException.InvalidScore, AssessmentException.ScoreWithoutSubmission,
        AssessmentException.InvalidQuestion, AssessmentException.NotEnrolledInClass,
        AssessmentException.MiniTaskInactive, AssessmentException.AnswerCountMismatch,
        AssessmentException.ClassClosed {

    protected AssessmentException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    public static final class ClassNotFound extends AssessmentException {
        public ClassNotFound(UUID classId) {
            super("ASSESSMENT_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
        }
    }

    /** Giao bài/mini-task mới cho lớp đã kết thúc hoặc đã huỷ. */
    public static final class ClassClosed extends AssessmentException {
        public ClassClosed(UUID classId) {
            super("ASSESSMENT_CLASS_CLOSED", HttpStatus.CONFLICT,
                    "Lớp " + classId + " đã kết thúc hoặc đã huỷ, không giao bài mới được");
        }
    }

    public static final class NotAssignedToClass extends AssessmentException {
        public NotAssignedToClass(UUID actorAccountId, UUID classId) {
            super("ASSESSMENT_NOT_ASSIGNED_TO_CLASS", HttpStatus.FORBIDDEN,
                    "Tài khoản " + actorAccountId + " không được phân công dạy lớp " + classId);
        }
    }

    public static final class AssignmentNotFound extends AssessmentException {
        public AssignmentNotFound(UUID assignmentId) {
            super("ASSESSMENT_ASSIGNMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                    "Không tìm thấy bài tập " + assignmentId);
        }
    }

    public static final class MiniTaskNotFound extends AssessmentException {
        public MiniTaskNotFound(UUID miniTaskId) {
            super("ASSESSMENT_MINI_TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy mini-task " + miniTaskId);
        }
    }

    public static final class EnrollmentNotInClass extends AssessmentException {
        public EnrollmentNotInClass(UUID enrollmentId, UUID classId) {
            super("ASSESSMENT_ENROLLMENT_NOT_IN_CLASS", HttpStatus.BAD_REQUEST,
                    "Lượt ghi danh " + enrollmentId + " không thuộc lớp " + classId);
        }
    }

    public static final class DuplicateEntry extends AssessmentException {
        public DuplicateEntry(UUID enrollmentId) {
            super("ASSESSMENT_DUPLICATE_ENTRY", HttpStatus.BAD_REQUEST,
                    "Lượt ghi danh " + enrollmentId + " xuất hiện hai lần trong cùng một lần nhập kết quả");
        }
    }

    /** Thang điểm là của RIÊNG từng bài, nên lỗi nói ra đúng thang đó - mirror InvalidScoreException. */
    public static final class InvalidScore extends AssessmentException {
        public InvalidScore(BigDecimal score, BigDecimal maxScore) {
            super("ASSESSMENT_INVALID_SCORE", HttpStatus.BAD_REQUEST,
                    "Điểm " + score.toPlainString() + " phải nằm trong khoảng ["
                            + AssessmentConstants.Scoring.MIN_SCORE.toPlainString() + ", "
                            + maxScore.toPlainString() + "] của bài này");
        }
    }

    public static final class ScoreWithoutSubmission extends AssessmentException {
        public ScoreWithoutSubmission(UUID enrollmentId) {
            super("ASSESSMENT_SCORE_WITHOUT_SUBMISSION", HttpStatus.BAD_REQUEST,
                    "Học viên của lượt ghi danh " + enrollmentId + " chưa nộp bài nên không thể có điểm");
        }
    }

    public static final class InvalidQuestion extends AssessmentException {
        public InvalidQuestion(int questionNumber) {
            super("ASSESSMENT_INVALID_QUESTION", HttpStatus.BAD_REQUEST,
                    "Câu hỏi số " + questionNumber + " cần từ " + AssessmentConstants.Limits.MIN_OPTIONS + " đến "
                            + AssessmentConstants.Limits.MAX_OPTIONS
                            + " phương án và đáp án đúng phải là một trong các phương án đó");
        }
    }

    /** Học viên chỉ làm được mini-task của lớp mình đang học (không tính lớp đã rút). */
    public static final class NotEnrolledInClass extends AssessmentException {
        public NotEnrolledInClass(UUID classId) {
            super("ASSESSMENT_NOT_ENROLLED_IN_CLASS", HttpStatus.FORBIDDEN,
                    "Bạn không học lớp " + classId + " nên không thể làm mini-task này");
        }
    }

    public static final class MiniTaskInactive extends AssessmentException {
        public MiniTaskInactive(UUID miniTaskId) {
            super("ASSESSMENT_MINI_TASK_INACTIVE", HttpStatus.CONFLICT,
                    "Mini-task " + miniTaskId + " đã bị giáo viên tắt");
        }
    }

    public static final class AnswerCountMismatch extends AssessmentException {
        public AnswerCountMismatch(int expected, int actual) {
            super("ASSESSMENT_ANSWER_COUNT_MISMATCH", HttpStatus.BAD_REQUEST,
                    "Mini-task có " + expected + " câu nhưng bài nộp có " + actual + " câu trả lời");
        }
    }
}
