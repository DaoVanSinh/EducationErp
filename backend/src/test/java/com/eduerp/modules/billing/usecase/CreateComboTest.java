package com.eduerp.modules.billing.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.MinimumComboSizeException;
import com.eduerp.modules.billing.StudentMismatchInComboException;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboDiscountTier;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class CreateComboTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);
    private static final BigDecimal MATHS_FEE = new BigDecimal("12000000");
    private static final BigDecimal ENGLISH_FEE = new BigDecimal("9000000");

    private final ComboRepository combos = mock(ComboRepository.class);
    private final ComboDiscountTierRepository tiers = mock(ComboDiscountTierRepository.class);
    private final EnrollmentManagement enrollmentManagement = mock(EnrollmentManagement.class);
    private final CoursesManagement coursesManagement = mock(CoursesManagement.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CreateCombo useCase =
            new CreateCombo(combos, tiers, enrollmentManagement, coursesManagement, events);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID enrollmentBranchId = UUID.randomUUID();
    private final UUID mathsEnrollmentId = UUID.randomUUID();
    private final UUID englishEnrollmentId = UUID.randomUUID();
    private final UUID mathsCourseId = UUID.randomUUID();
    private final UUID englishCourseId = UUID.randomUUID();

    private CreateComboRequest request(UUID... enrollmentIds) {
        return new CreateComboRequest(studentProfileId, List.of(enrollmentIds), DUE_DATE);
    }

    private void stubEnrollment(UUID enrollmentId, UUID courseId, UUID ownerStudentProfileId, UUID branchId,
            EnrollmentConstants.EnrollmentStatus status) {
        when(enrollmentManagement.getEnrollment(enrollmentId)).thenReturn(Optional.of(
                new EnrollmentManagement.EnrollmentSummaryResponse(enrollmentId, ownerStudentProfileId, courseId,
                        branchId, status)));
    }

    private void stubTwoActiveEnrollments() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }

    private void stubTuition(UUID courseId, BigDecimal tuitionFee) {
        when(coursesManagement.getCourseTuition(courseId)).thenReturn(Optional.of(
                new CoursesManagement.CourseTuitionResponse(courseId, tuitionFee, true)));
    }

    private void stubBothTuitions() {
        stubTuition(mathsCourseId, MATHS_FEE);
        stubTuition(englishCourseId, ENGLISH_FEE);
    }

    private void stubTier(int minCourseCount, String discountPercent) {
        when(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(anyInt()))
                .thenReturn(Optional.of(new ComboDiscountTier(minCourseCount, new BigDecimal(discountPercent))));
    }

    private void stubSaveEchoesBack() {
        when(combos.saveAndFlush(any(Combo.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Review Focus #2: một "combo" một phần tử không phải combo - đã có CreateInvoice cho việc đó. */
    @Test
    void rejectsAComboOfASingleEnrollment() {
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId, request(mathsEnrollmentId)))
                .isInstanceOf(MinimumComboSizeException.class)
                .hasMessageContaining("2");

        verify(enrollmentManagement, never()).getEnrollment(any());
    }

    /** Review Focus #2, mặt dễ lọt: hai phần tử nhưng là CÙNG MỘT ghi danh vẫn chỉ là một khoá. */
    @Test
    void rejectsAComboBuiltFromTheSameEnrollmentTwice() {
        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, mathsEnrollmentId)))
                .isInstanceOf(MinimumComboSizeException.class);
    }

    /**
     * Review Focus #1: gộp ghi danh của hai học viên khác nhau phải bị CHẶN, không được âm thầm lấy
     * học viên của ghi danh đầu tiên - nếu không, hệ thống phát hành một công nợ cho người này dựa
     * trên khoá học của người kia.
     */
    @Test
    void rejectsAComboMixingTwoDifferentStudents() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, UUID.randomUUID(), enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class)
                .hasMessageContaining(englishEnrollmentId.toString());

        verify(combos, never()).saveAndFlush(any());
    }

    /** Review Focus #1, biến thể: ghi danh ĐẦU TIÊN mới là người không khớp studentProfileId trong
     * body - không được bỏ qua chỉ vì nó là mốc so sánh branchId. */
    @Test
    void rejectsWhenTheFirstEnrollmentItselfBelongsToAnotherStudent() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, UUID.randomUUID(), enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class)
                .hasMessageContaining(mathsEnrollmentId.toString());
    }

    /** branchId của combo lấy từ ghi danh ĐẦU TIÊN, không phải actorBranchId - kế toán cấp tổ chức
     * tạo được combo cho chi nhánh khác, nhưng mọi khoá trong combo phải cùng một chi nhánh. */
    @Test
    void rejectsAComboMixingTwoBranchesEvenForTheSameStudent() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, UUID.randomUUID(),
                EnrollmentConstants.EnrollmentStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(StudentMismatchInComboException.class);
    }

    @Test
    void rejectsAnUnknownEnrollment() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        when(enrollmentManagement.getEnrollment(englishEnrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    /** Chỉ gộp được ghi danh ĐANG HỌC - một khoá đã rút không còn là thứ để bán gói (spec mục 6). */
    @Test
    void rejectsAWithdrawnEnrollment() {
        stubEnrollment(mathsEnrollmentId, mathsCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE);
        stubEnrollment(englishEnrollmentId, englishCourseId, studentProfileId, enrollmentBranchId,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentNotActiveForBillingException.class);
    }

    @Test
    void sumsTuitionFeesAppliesTheTierAndPublishesComboCreated() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        stubSaveEchoesBack();

        var response = useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId));

        assertThat(response.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(response.branchId()).isEqualTo(enrollmentBranchId);
        assertThat(response.totalOriginalAmount()).isEqualByComparingTo(new BigDecimal("21000000"));
        assertThat(response.discountPercent()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(response.totalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
        assertThat(response.dueDate()).isEqualTo(DUE_DATE);
        assertThat(response.courseCount()).isEqualTo(2);
        verify(events).publishEvent(any(BillingEvents.ComboCreated.class));
    }

    /** Bậc giảm giá chọn theo SỐ KHOÁ SAU KHI LOẠI TRÙNG, không theo độ dài danh sách gửi lên. */
    @Test
    void picksTheTierByTheDeduplicatedCourseCount() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        stubSaveEchoesBack();

        useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId, mathsEnrollmentId));

        verify(tiers).findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2);
    }

    /**
     * Review Focus #4: chưa cấu hình bậc nào thoả số khoá này thì CHẶN HẲN, không mặc định 0% - mirror
     * CourseTuitionNotConfiguredException của Phase 3. Nếu lặng lẽ giảm 0% thì kế toán tưởng đã bán
     * gói giảm giá, còn học viên trả nguyên giá.
     */
    @Test
    void rejectsACombinationWithNoConfiguredDiscountTier() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        when(tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(2))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(ComboDiscountTierNotConfiguredException.class)
                .hasMessageContaining("2");

        verify(combos, never()).saveAndFlush(any());
        verify(events, never()).publishEvent(any());
    }

    @Test
    void rejectsACourseWithoutATuitionFee() {
        stubTwoActiveEnrollments();
        stubTuition(mathsCourseId, MATHS_FEE);
        stubTuition(englishCourseId, null);

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(CourseTuitionNotConfiguredException.class)
                .hasMessageContaining(englishCourseId.toString());
    }

    @Test
    void rejectsAnEnrollmentWhoseCourseIsGone() {
        stubTwoActiveEnrollments();
        stubTuition(mathsCourseId, MATHS_FEE);
        when(coursesManagement.getCourseTuition(englishCourseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(CourseNotFoundException.class);
    }

    /**
     * Review Focus #3: khi race lọt qua mọi kiểm tra ở trên (cả hai request đọc cùng một trạng thái
     * trước khi ai kịp ghi), UNIQUE combo_enrollments.enrollment_id chặn một trong hai ở bước lưu -
     * usecase phải dịch sang lỗi nghiệp vụ, KHÔNG để lọt ra thành 500.
     */
    @Test
    void translatesADatabaseRaceOnASharedEnrollmentIntoEnrollmentAlreadyInCombo() {
        stubTwoActiveEnrollments();
        stubBothTuitions();
        stubTier(2, "15");
        when(combos.saveAndFlush(any(Combo.class)))
                .thenThrow(new DataIntegrityViolationException("combo_enrollments_enrollment_id_key"));

        assertThatThrownBy(() -> useCase.execute(actorAccountId, actorBranchId,
                request(mathsEnrollmentId, englishEnrollmentId)))
                .isInstanceOf(EnrollmentAlreadyInComboException.class);

        verify(events, never()).publishEvent(any());
    }
}
