package com.eduerp.modules.billing.internal.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InvoiceResponseAssemblerTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2027, 1, 31);
    private static final BigDecimal AMOUNT = new BigDecimal("1000000");

    private final ComboRepository combos = mock(ComboRepository.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final StudentsManagement students = mock(StudentsManagement.class);
    private final InvoiceResponseAssembler assembler = new InvoiceResponseAssembler(combos, courses, students);

    private final UUID studentId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID mathsCourseId = UUID.randomUUID();
    private final UUID englishCourseId = UUID.randomUUID();
    private final UUID comboId = UUID.randomUUID();

    private Invoice enrollmentInvoice() {
        return new Invoice(UUID.randomUUID(), studentId, mathsCourseId, branchId, 1, AMOUNT, DUE_DATE, actorId);
    }

    private Invoice comboInvoice() {
        return Invoice.forCombo(comboId, studentId, branchId, 1, AMOUNT, DUE_DATE, actorId);
    }

    private void stubComboWithTwoCourses() {
        var combo = new Combo(studentId, branchId, AMOUNT, BigDecimal.TEN, DUE_DATE, actorId);
        ReflectionTestUtils.setField(combo, "id", comboId);
        combo.addEnrollment(UUID.randomUUID(), mathsCourseId, AMOUNT);
        combo.addEnrollment(UUID.randomUUID(), englishCourseId, AMOUNT);
        when(combos.findAllById(List.of(comboId))).thenReturn(List.of(combo));
    }

    @Test
    void anEnrollmentInvoiceNamesItsStudentAndItsOneCourse() {
        when(students.displayNamesOf(any())).thenReturn(Map.of(studentId, "Nguyễn Văn A"));
        when(courses.courseNamesOf(any())).thenReturn(Map.of(mathsCourseId, "Toán"));

        var response = assembler.toResponse(enrollmentInvoice());

        assertThat(response.studentName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.courseNames()).containsExactly("Toán");
    }

    @Test
    void aComboInvoiceNamesEveryCourseInTheCombo() {
        stubComboWithTwoCourses();
        when(courses.courseNamesOf(any())).thenReturn(Map.of(mathsCourseId, "Toán", englishCourseId, "Anh"));

        assertThat(assembler.toResponse(comboInvoice()).courseNames()).containsExactly("Toán", "Anh");
    }

    @Test
    void namesThatCannotBeResolvedStayEmptyInsteadOfFailing() {
        var response = assembler.toResponse(enrollmentInvoice());

        assertThat(response.studentName()).isNull();
        assertThat(response.courseNames()).isEmpty();
    }

    @Test
    void aWholeListLooksUpNamesOnlyOnce() {
        stubComboWithTwoCourses();

        assembler.toResponses(List.of(enrollmentInvoice(), enrollmentInvoice(), comboInvoice()));

        verify(students, times(1)).displayNamesOf(List.of(studentId));
        verify(courses, times(1)).courseNamesOf(List.of(mathsCourseId, englishCourseId));
    }
}
