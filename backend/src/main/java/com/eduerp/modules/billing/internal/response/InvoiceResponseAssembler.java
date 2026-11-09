package com.eduerp.modules.billing.internal.response;

import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Một chỗ duy nhất biến {@link Invoice} thành {@link InvoiceResponse}. Hoá đơn chỉ lưu id: tên học viên và tên
 * khoá thuộc module khác nên gom id của cả danh sách, tra một lần qua facade rồi ghép ở đây. Hoá đơn combo không
 * có {@code courseId}, nên danh sách khoá của nó lấy từ chính combo.
 */
@Component
public class InvoiceResponseAssembler {

    private final ComboRepository combos;
    private final CoursesManagement courses;
    private final StudentsManagement students;

    public InvoiceResponseAssembler(ComboRepository combos, CoursesManagement courses, StudentsManagement students) {
        this.combos = combos;
        this.courses = courses;
        this.students = students;
    }

    public InvoiceResponse toResponse(Invoice invoice) {
        return toResponses(List.of(invoice)).get(0);
    }

    public List<InvoiceResponse> toResponses(List<Invoice> invoices) {
        var studentNames = students.displayNamesOf(
                invoices.stream().map(Invoice::getStudentProfileId).distinct().toList());
        var studentSummaries = students.summariesOf(
                invoices.stream().map(Invoice::getStudentProfileId).distinct().toList());
        var courseIdsByCombo = courseIdsByCombo(invoices);
        var courseNames = courses.courseNamesOf(invoices.stream()
                .flatMap(invoice -> courseIdsOf(invoice, courseIdsByCombo).stream())
                .distinct().toList());
        return invoices.stream()
                .map(invoice -> toResponse(invoice, studentNames.get(invoice.getStudentProfileId()),
                        namesOf(courseIdsOf(invoice, courseIdsByCombo), courseNames),
                        studentSummaries.get(invoice.getStudentProfileId())))
                .toList();
    }

    private Map<UUID, List<UUID>> courseIdsByCombo(List<Invoice> invoices) {
        var comboIds = invoices.stream().map(Invoice::getComboId).filter(Objects::nonNull).distinct().toList();
        return combos.findAllById(comboIds).stream().collect(Collectors.toMap(Combo::getId,
                combo -> combo.getEnrollments().stream().map(ComboEnrollment::getCourseId).toList()));
    }

    private static List<UUID> courseIdsOf(Invoice invoice, Map<UUID, List<UUID>> courseIdsByCombo) {
        if (invoice.getComboId() != null) {
            return courseIdsByCombo.getOrDefault(invoice.getComboId(), List.of());
        }
        return invoice.getCourseId() == null ? List.of() : List.of(invoice.getCourseId());
    }

    private static List<String> namesOf(List<UUID> courseIds, Map<UUID, String> courseNames) {
        return courseIds.stream().map(courseNames::get).filter(Objects::nonNull).toList();
    }

    private static InvoiceResponse toResponse(Invoice invoice, String studentName, List<String> courseNames,
            StudentsManagement.StudentSummaryResponse student) {
        return new InvoiceResponse(invoice.getId(), invoice.getEnrollmentId(), invoice.getComboId(),
                invoice.getStudentProfileId(), studentName, invoice.getCourseId(), courseNames, invoice.getBranchId(),
                invoice.getInstallmentNumber(), invoice.getAmount(), invoice.getAmountPaid(), invoice.getStatus(),
                invoice.getDueDate(), invoice.getIssuedAt(), invoice.isDueExtended(), invoice.getExtensionReason(),
                student == null ? null : student.accountId());
    }
}
