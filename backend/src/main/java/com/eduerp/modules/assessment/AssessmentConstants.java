package com.eduerp.modules.assessment;

import java.math.BigDecimal;

/** Hằng số của module assessment - mirror {@code AcademicConstants}. */
public final class AssessmentConstants {

    private AssessmentConstants() {
    }

    /** Bài tập về nhà hoặc mini-test định kỳ - cùng cách theo dõi, chỉ khác nhãn hiển thị. */
    public enum AssignmentType {
        HOMEWORK, MINI_TEST
    }

    /** Tình trạng nộp bài. {@code SUBMITTED} và {@code LATE} đều tính là đã hoàn thành. */
    public enum SubmissionStatus {
        SUBMITTED, LATE, NOT_SUBMITTED
    }

    public static final class Limits {
        private Limits() {
        }

        public static final int MAX_TITLE_LENGTH = 255;
        public static final int MAX_DESCRIPTION_LENGTH = 2000;
        public static final int MAX_FEEDBACK_LENGTH = 1000;
        public static final int MAX_QUESTION_LENGTH = 1000;
        public static final int MAX_OPTION_LENGTH = 500;
        public static final int MAX_EXPLANATION_LENGTH = 1000;
        /** Ngưỡng "Đạt" của mini-task (% số câu đúng) - khớp CHECK của V32. */
        public static final int MIN_PASS_PERCENT = 1;
        public static final int MAX_PASS_PERCENT = 100;
        public static final int DEFAULT_PASS_PERCENT = 80;
        public static final int MIN_QUESTIONS = 1;
        public static final int MAX_QUESTIONS = 50;
        public static final int MIN_OPTIONS = 2;
        public static final int MAX_OPTIONS = 6;
        public static final int PERCENT_SCALE = 100;
        /** Khớp NUMERIC(5,2) của {@code assignments.max_score}. */
        public static final String MAX_SCORE_CEILING = "999.99";
    }

    public static final class Scoring {
        private Scoring() {
        }

        public static final BigDecimal MIN_SCORE = BigDecimal.ZERO;
        public static final int SCORE_SCALE = 2;
    }

    /** Không cần cache namespace nào - đọc thẳng từ DB. */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }
    }
}
