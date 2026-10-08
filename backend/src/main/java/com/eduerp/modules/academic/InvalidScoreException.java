package com.eduerp.modules.academic;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

/** Review Focus #10: thang điểm là của RIÊNG từng cột, nên lỗi phải nói ra thang của đúng cột đó. */
public final class InvalidScoreException extends AcademicException {

    public InvalidScoreException(BigDecimal score, BigDecimal maxScore) {
        super("ACADEMIC_INVALID_SCORE", HttpStatus.BAD_REQUEST,
                "Điểm " + score.toPlainString() + " phải nằm trong khoảng ["
                        + AcademicConstants.Scoring.MIN_SCORE.toPlainString() + ", "
                        + maxScore.toPlainString() + "] của cột điểm này");
    }
}
