package com.eduerp.modules.assessment.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.assessment.AssessmentConstants.SubmissionStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentRulesTest {

    @Test
    void submittedAndLateBothCountAsDone() {
        assertThat(AssessmentRules.isSubmitted(SubmissionStatus.SUBMITTED)).isTrue();
        assertThat(AssessmentRules.isSubmitted(SubmissionStatus.LATE)).isTrue();
        assertThat(AssessmentRules.isSubmitted(SubmissionStatus.NOT_SUBMITTED)).isFalse();
    }

    @Test
    void aScoreIsOnlyAllowedOnceTheWorkIsSubmitted() {
        assertThat(AssessmentRules.allowsScore(SubmissionStatus.NOT_SUBMITTED, BigDecimal.ONE)).isFalse();
        assertThat(AssessmentRules.allowsScore(SubmissionStatus.NOT_SUBMITTED, null)).isTrue();
        assertThat(AssessmentRules.allowsScore(SubmissionStatus.LATE, BigDecimal.TEN)).isTrue();
    }

    @Test
    void acceptsBothEndsOfTheScoreRangeAndRejectsOutsideIt() {
        var max = new BigDecimal("10.00");
        assertThat(AssessmentRules.isScoreWithinRange(BigDecimal.ZERO, max)).isTrue();
        assertThat(AssessmentRules.isScoreWithinRange(max, max)).isTrue();
        assertThat(AssessmentRules.isScoreWithinRange(new BigDecimal("10.01"), max)).isFalse();
        assertThat(AssessmentRules.isScoreWithinRange(new BigDecimal("-0.5"), max)).isFalse();
    }

    @Test
    void percentIsRoundedAndNullWhenThereIsNothingToCount() {
        assertThat(AssessmentRules.percent(2, 3)).isEqualTo(67);
        assertThat(AssessmentRules.percent(0, 4)).isZero();
        assertThat(AssessmentRules.percent(0, 0)).isNull();
    }

    @Test
    void averageIgnoresMissingScoresAndIsNullWhenNoneExist() {
        assertThat(AssessmentRules.averageScore(Arrays.asList(new BigDecimal("8"), null, new BigDecimal("7"))))
                .isEqualByComparingTo("7.50");
        assertThat(AssessmentRules.averageScore(Arrays.asList(null, null))).isNull();
    }

    @Test
    void aQuestionNeedsTwoToSixOptionsAndAnAnswerAmongThem() {
        assertThat(AssessmentRules.isValidQuestion(4, 3)).isTrue();
        assertThat(AssessmentRules.isValidQuestion(4, 4)).isFalse();
        assertThat(AssessmentRules.isValidQuestion(1, 0)).isFalse();
        assertThat(AssessmentRules.isValidQuestion(7, 0)).isFalse();
        assertThat(AssessmentRules.isValidQuestion(2, -1)).isFalse();
    }

    @Test
    void countsMatchingAnswersAndTreatsBlanksAsWrong() {
        var correct = List.of(0, 2, 1, 3);
        var answers = Arrays.asList(0, 1, null, 3);

        assertThat(AssessmentRules.countCorrect(correct, answers)).isEqualTo(2);
    }
}
