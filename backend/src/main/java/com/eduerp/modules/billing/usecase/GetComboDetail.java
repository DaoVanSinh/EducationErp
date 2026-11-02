package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.ComboNotFoundException;
import com.eduerp.modules.billing.dto.ComboDetailResponse;
import com.eduerp.modules.billing.dto.ComboEnrollmentResponse;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code GetInvoiceDetail}: tổng thể + các danh sách con, một query cho mỗi phần, không N+1. */
@Service
public class GetComboDetail {

    private final ComboRepository combos;
    private final InvoiceRepository invoices;

    private final BillingBranchGuard branchGuard;

    private final StudentsManagement students;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final InvoiceResponseAssembler assembler;

    GetComboDetail(ComboRepository combos, InvoiceRepository invoices, BillingBranchGuard branchGuard,
            StudentsManagement students, EnrollmentManagement enrollments, CoursesManagement courses,
            InvoiceResponseAssembler assembler) {
        this.assembler = assembler;
        this.combos = combos;
        this.invoices = invoices;
        this.branchGuard = branchGuard;
        this.students = students;
        this.enrollments = enrollments;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public ComboDetailResponse execute(UUID actorAccountId, UUID comboId) {
        var combo = combos.findById(comboId).orElseThrow(() -> new ComboNotFoundException(comboId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.READ, combo.getBranchId());
        var classes = classesByEnrollment(combo);
        // Thứ tự các khoá do @OrderBy trên Combo.enrollments quyết định - không sort lại ở đây.
        var members = combo.getEnrollments().stream()
                .map(member -> toMember(member, classes.get(member.getEnrollmentId())))
                .toList();
        // findAllByComboId KHÔNG lọc trạng thái: kế toán cần thấy cả đợt đã huỷ để hiểu vì sao số đợt
        // nhảy số (spec mục 6).
        var comboInvoices = assembler.toResponses(invoices.findAllByComboId(comboId));
        var studentName = students.displayNamesOf(List.of(combo.getStudentProfileId())).get(combo.getStudentProfileId());
        return new ComboDetailResponse(CreateCombo.toResponse(combo, studentName), members, comboInvoices);
    }

    /**
     * Lớp của từng ghi danh trong combo, theo {@code enrollmentId}. Ghi danh và lớp thuộc hai module khác,
     * nên đọc qua facade rồi ghép ở đây thay vì JOIN: mọi ghi danh của combo đều thuộc cùng một học viên,
     * và {@code listEnrollmentsByStudent} trả cả ghi danh đã rút.cho
     */
    private Map<UUID, CoursesManagement.ClassBasicSummary> classesByEnrollment(Combo combo) {
        var memberIds = combo.getEnrollments().stream().map(ComboEnrollment::getEnrollmentId)
                .collect(Collectors.toSet());
        var classIds = enrollments.listEnrollmentsByStudent(combo.getStudentProfileId()).stream()
                .filter(entry -> memberIds.contains(entry.enrollmentId()))
                .collect(Collectors.toMap(EnrollmentManagement.StudentEnrollmentEntry::enrollmentId,
                        EnrollmentManagement.StudentEnrollmentEntry::classId));
        var summaries = courses.classSummariesOf(classIds.values());
        var result = new HashMap<UUID, CoursesManagement.ClassBasicSummary>();
        classIds.forEach((enrollmentId, classId) -> result.put(enrollmentId, summaries.get(classId)));
        return result;
    }

    private static ComboEnrollmentResponse toMember(ComboEnrollment member,
            CoursesManagement.ClassBasicSummary classSummary) {
        var courseName = classSummary == null ? null : classSummary.courseName();
        var classCode = classSummary == null ? null : classSummary.code();
        return new ComboEnrollmentResponse(member.getId(), member.getEnrollmentId(), member.getCourseId(),
                courseName, classCode, member.getOriginalTuitionFee());
    }
}
