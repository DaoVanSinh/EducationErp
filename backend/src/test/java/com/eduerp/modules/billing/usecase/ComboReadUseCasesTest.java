package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.ComboEnrollmentResponse;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class ComboReadUseCasesTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);

    private final ComboRepository combos = mock(ComboRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final StudentsManagement students = mock(StudentsManagement.class);
    private final ListCombos listCombos = new ListCombos(combos, mock(BillingBranchGuard.class), students);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final GetComboDetail getComboDetail =
            new GetComboDetail(combos, invoices, mock(BillingBranchGuard.class), students, enrollments, courses,
                    new InvoiceResponseAssembler(combos, courses, students));

    private final UUID comboId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID mathsEnrollmentId = UUID.randomUUID();
    private final UUID englishEnrollmentId = UUID.randomUUID();
    private final UUID mathsCourseId = UUID.randomUUID();
    private final UUID englishCourseId = UUID.randomUUID();

    private Combo comboWithTwoCourses() {
        var combo = new Combo(studentProfileId, branchId, new BigDecimal("21000000"), new BigDecimal("15"),
                DUE_DATE, actorId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        combo.addEnrollment(mathsEnrollmentId, mathsCourseId, new BigDecimal("12000000"));
        combo.addEnrollment(englishEnrollmentId, englishCourseId, new BigDecimal("9000000"));
        return combo;
    }

    @Test
    void listPassesTheOptionalStudentFilterStraightToTheRepositoryAndMapsThePage() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(studentProfileId, null, pageable))
                .thenReturn(new PageImpl<>(List.of(comboWithTwoCourses()), pageable, 1));

        var page = listCombos.execute(null, pageable, studentProfileId, null);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(comboId);
            assertThat(item.totalDiscountedAmount()).isEqualByComparingTo(new BigDecimal("17850000"));
            // courseCount trả sẵn để màn hình danh sách không phải tải danh sách con chỉ để đếm.
            assertThat(item.courseCount()).isEqualTo(2);
        });
    }

    @Test
    void listLooksUpStudentNamesOnceForTheWholePage() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(comboWithTwoCourses(), comboWithTwoCourses()), pageable, 2));
        when(students.displayNamesOf(List.of(studentProfileId))).thenReturn(Map.of(studentProfileId, "Nguyễn Văn A"));

        var page = listCombos.execute(null, pageable, null, null);

        assertThat(page.items()).hasSize(2).allSatisfy(item -> assertThat(item.studentName()).isEqualTo("Nguyễn Văn A"));
        verify(students, times(1)).displayNamesOf(List.of(studentProfileId));
    }

    @Test
    void listLeavesTheNameNullWhenTheStudentCannotBeResolved() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(comboWithTwoCourses()), pageable, 1));
        when(students.displayNamesOf(List.of(studentProfileId))).thenReturn(Map.of());

        assertThat(listCombos.execute(null, pageable, null, null).items())
                .singleElement().satisfies(item -> assertThat(item.studentName()).isNull());
    }

    @Test
    void detailIncludesTheStudentName() {
        when(combos.findById(comboId)).thenReturn(Optional.of(comboWithTwoCourses()));
        when(invoices.findAllByComboId(comboId)).thenReturn(List.of());
        when(students.displayNamesOf(List.of(studentProfileId))).thenReturn(Map.of(studentProfileId, "Nguyễn Văn A"));

        assertThat(getComboDetail.execute(null, comboId).combo().studentName()).isEqualTo("Nguyễn Văn A");
    }

    private EnrollmentManagement.StudentEnrollmentEntry enrollmentIn(UUID enrollmentId, UUID classId, UUID courseId) {
        return new EnrollmentManagement.StudentEnrollmentEntry(enrollmentId, classId, courseId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, Instant.now(), null);
    }

    @Test
    void detailNamesEachCourseAndClassAndIgnoresEnrollmentsOutsideTheCombo() {
        var mathsClassId = UUID.randomUUID();
        var englishClassId = UUID.randomUUID();
        var otherClassId = UUID.randomUUID();
        when(combos.findById(comboId)).thenReturn(Optional.of(comboWithTwoCourses()));
        when(invoices.findAllByComboId(comboId)).thenReturn(List.of());
        when(enrollments.listEnrollmentsByStudent(studentProfileId)).thenReturn(List.of(
                enrollmentIn(mathsEnrollmentId, mathsClassId, mathsCourseId),
                enrollmentIn(englishEnrollmentId, englishClassId, englishCourseId),
                enrollmentIn(UUID.randomUUID(), otherClassId, UUID.randomUUID())));
        when(courses.classSummariesOf(any())).thenAnswer(call -> {
            assertThat(call.<Collection<UUID>>getArgument(0)).containsExactlyInAnyOrder(mathsClassId, englishClassId);
            return Map.of(
                    mathsClassId, new CoursesManagement.ClassBasicSummary(mathsClassId, "TOAN-01", mathsCourseId, "Toán"),
                    englishClassId, new CoursesManagement.ClassBasicSummary(englishClassId, "ANH-01", englishCourseId, "Anh"));
        });

        var members = getComboDetail.execute(null, comboId).enrollments();

        assertThat(members).extracting(ComboEnrollmentResponse::courseName).containsExactly("Toán", "Anh");
        assertThat(members).extracting(ComboEnrollmentResponse::classCode).containsExactly("TOAN-01", "ANH-01");
    }

    @Test
    void detailLeavesNamesNullWhenTheClassCannotBeResolved() {
        when(combos.findById(comboId)).thenReturn(Optional.of(comboWithTwoCourses()));
        when(invoices.findAllByComboId(comboId)).thenReturn(List.of());
        when(enrollments.listEnrollmentsByStudent(studentProfileId)).thenReturn(List.of(
                enrollmentIn(mathsEnrollmentId, UUID.randomUUID(), mathsCourseId)));
        when(courses.classSummariesOf(any())).thenReturn(Map.of());

        var members = getComboDetail.execute(null, comboId).enrollments();

        assertThat(members).hasSize(2).allSatisfy(member -> {
            assertThat(member.courseName()).isNull();
            assertThat(member.classCode()).isNull();
        });
    }

    @Test
    void listAcceptsANullStudentFilter() {
        var pageable = PageRequest.of(0, 20);
        when(combos.search(null, null, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        assertThat(listCombos.execute(null, pageable, null, null).items()).isEmpty();
    }

    @Test
    void detailReturnsTheComboItsCoursesAndEveryInvoiceIncludingCancelledOnes() {
        var combo = comboWithTwoCourses();
        var paid = Invoice.forCombo(comboId, studentProfileId, branchId, 1, new BigDecimal("10000000"),
                DUE_DATE, actorId);
        var cancelled = Invoice.forCombo(comboId, studentProfileId, branchId, 2, new BigDecimal("7850000"),
                DUE_DATE, actorId);
        cancelled.cancel();
        when(combos.findById(comboId)).thenReturn(Optional.of(combo));
        when(invoices.findAllByComboId(comboId)).thenReturn(List.of(paid, cancelled));

        var detail = getComboDetail.execute(null, comboId);

        assertThat(detail.combo().id()).isEqualTo(comboId);
        assertThat(detail.combo().courseCount()).isEqualTo(2);
        // @OrderBy("originalTuitionFee DESC") trên Combo.enrollments quyết định thứ tự - không sort
        // lại ở usecase.
        assertThat(detail.enrollments()).extracting(ComboEnrollmentResponse::enrollmentId)
                .containsExactly(mathsEnrollmentId, englishEnrollmentId);
        assertThat(detail.enrollments()).extracting(ComboEnrollmentResponse::originalTuitionFee)
                .containsExactly(new BigDecimal("12000000"), new BigDecimal("9000000"));
        // Chi tiết combo hiển thị cả hoá đơn đã huỷ: kế toán cần thấy vì sao số đợt lại nhảy số.
        assertThat(detail.invoices()).hasSize(2);
        assertThat(detail.invoices().get(0).comboId()).isEqualTo(comboId);
        assertThat(detail.invoices().get(0).enrollmentId()).isNull();
    }

    @Test
    void detailRejectsAnUnknownCombo() {
        when(combos.findById(comboId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getComboDetail.execute(null, comboId))
                .isInstanceOf(ComboNotFoundException.class);
    }
}
