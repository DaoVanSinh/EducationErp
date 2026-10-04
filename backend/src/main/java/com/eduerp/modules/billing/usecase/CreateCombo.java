package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyInComboException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.MinimumComboSizeException;
import com.eduerp.modules.billing.StudentMismatchInComboException;
import com.eduerp.modules.billing.dto.ComboEnrollmentResponse;
import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.internal.model.Combo;
import com.eduerp.modules.billing.internal.model.ComboEnrollment;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.ComboRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gộp các ghi danh ĐANG ACTIVE của một học viên thành một gói giảm giá theo số khoá (spec mục 6).
 *
 * <p>KHÔNG tạo ghi danh nào: kế toán ghi danh từng khoá bằng luồng {@code CreateEnrollment} có sẵn
 * trước, rồi mới gộp. Usecase này chỉ ĐỌC {@code EnrollmentManagement.getEnrollment} - đúng mirror
 * cách {@code CreateInvoice} làm, không cần thêm một quyền ghi xuyên module nào (spec mục 3).
 *
 * <p>Thứ tự kiểm tra: số lượng → ghi danh tồn tại/ACTIVE → cùng học viên và cùng chi nhánh → học phí
 * từng khoá → bậc giảm giá → lưu. Dừng ở bước đầu tiên sai, không gom lỗi.
 */
@Service
public class CreateCombo {

    private final ComboRepository combos;
    private final ComboDiscountTierRepository tiers;
    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final ApplicationEventPublisher events;

    CreateCombo(ComboRepository combos, ComboDiscountTierRepository tiers,
            EnrollmentManagement enrollmentManagement, CoursesManagement coursesManagement,
            ApplicationEventPublisher events) {
        this.combos = combos;
        this.tiers = tiers;
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.events = events;
    }

    @Transactional
    public ComboResponse execute(UUID actorAccountId, UUID actorBranchId, CreateComboRequest request) {
        // LinkedHashSet: loại trùng (hai lần cùng một id vẫn chỉ là MỘT khoá - Review Focus #2) mà
        // vẫn giữ thứ tự người dùng gửi lên, vì phần tử đầu tiên quyết định branchId của combo.
        var enrollmentIds = List.copyOf(new LinkedHashSet<>(request.enrollmentIds()));
        if (enrollmentIds.size() < BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO) {
            throw new MinimumComboSizeException(BillingConstants.Limits.MIN_ENROLLMENTS_PER_COMBO);
        }

        var members = enrollmentIds.stream().map(this::loadActiveEnrollment).toList();
        // branchId lấy từ ghi danh ĐẦU TIÊN, không phải actorBranchId: kế toán cấp tổ chức tạo được
        // combo cho bất kỳ chi nhánh nào - mirror cách CreateInvoice lấy branchId từ enrollment.
        var comboBranchId = members.get(0).branchId();
        members.forEach(member -> requireSameStudentAndBranch(member, request.studentProfileId(), comboBranchId));

        var priced = members.stream().map(this::price).toList();
        var totalOriginalAmount = priced.stream().map(PricedEnrollment::tuitionFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var tier = tiers
                .findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(priced.size())
                .orElseThrow(() -> new ComboDiscountTierNotConfiguredException(priced.size()));

        var combo = new Combo(request.studentProfileId(), comboBranchId, totalOriginalAmount,
                tier.getDiscountPercent(), request.dueDate(), actorAccountId);
        priced.forEach(item -> combo.addEnrollment(item.enrollmentId(), item.courseId(), item.tuitionFee()));

        var saved = save(combo, enrollmentIds);
        events.publishEvent(new BillingEvents.ComboCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    private EnrollmentManagement.EnrollmentSummaryResponse loadActiveEnrollment(UUID enrollmentId) {
        var enrollment = enrollmentManagement.getEnrollment(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(enrollmentId);
        }
        return enrollment;
    }

    /** Review Focus #1: kiểm CẢ ghi danh đầu tiên (nó là mốc so branchId, nhưng studentProfileId của
     * nó vẫn phải khớp đúng cái client gửi lên - không suy ngược từ dữ liệu). */
    private static void requireSameStudentAndBranch(EnrollmentManagement.EnrollmentSummaryResponse enrollment,
            UUID studentProfileId, UUID comboBranchId) {
        if (!enrollment.studentProfileId().equals(studentProfileId)
                || !enrollment.branchId().equals(comboBranchId)) {
            throw new StudentMismatchInComboException(enrollment.enrollmentId());
        }
    }

    private PricedEnrollment price(EnrollmentManagement.EnrollmentSummaryResponse enrollment) {
        var tuition = coursesManagement.getCourseTuition(enrollment.courseId())
                .orElseThrow(() -> new CourseNotFoundException(enrollment.courseId()));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(enrollment.courseId());
        }
        return new PricedEnrollment(enrollment.enrollmentId(), enrollment.courseId(), tuition.tuitionFee());
    }

    /**
     * Review Focus #3: hai request đồng thời cùng chọn một ghi danh đều đọc xong trước khi ai kịp
     * ghi, nên mọi kiểm tra ở trên đều qua. UNIQUE {@code combo_enrollments.enrollment_id} (V21) là
     * lớp chặn cuối - {@code saveAndFlush} để INSERT chạy NGAY trong khối try này thay vì trôi tới
     * lúc commit (ngoài tầm với của catch) rồi rơi thành 500. Mirror {@code CreateInvoice.saveInvoice}.
     */
    private Combo save(Combo combo, List<UUID> enrollmentIds) {
        try {
            return combos.saveAndFlush(combo);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new EnrollmentAlreadyInComboException(enrollmentIds);
        }
    }

    /** Giữ đúng bộ ba (ghi danh, khoá, học phí) đi cùng nhau thay vì ghép lại bằng chỉ số của hai
     * danh sách song song - một chỗ lệch chỉ số là một học viên bị tính sai tiền. */
    private record PricedEnrollment(UUID enrollmentId, UUID courseId, BigDecimal tuitionFee) {
    }

    /** Dùng lại ở {@code ListCombos}/{@code GetComboDetail} - một chỗ map duy nhất cho Combo
     * (mirror {@code CreateInvoice.toResponse}). */
    static ComboResponse toResponse(Combo combo) {
        return new ComboResponse(combo.getId(), combo.getStudentProfileId(), combo.getBranchId(),
                combo.getTotalOriginalAmount(), combo.getDiscountPercent(), combo.getTotalDiscountedAmount(),
                combo.getDueDate(), combo.getCreatedAt(), combo.getEnrollments().size());
    }

    /**
     * Tên khác {@code toResponse} một cách cố ý: hai static method cùng tên, một nhận {@code Combo}
     * một nhận {@code ComboEnrollment}, sẽ biến mọi method reference {@code CreateCombo::toResponse}
     * thành một bài toán suy luận overload - đặt tên riêng thì người đọc và trình biên dịch đều khỏi
     * phải đoán.
     */
    static ComboEnrollmentResponse toEnrollmentResponse(ComboEnrollment member) {
        return new ComboEnrollmentResponse(member.getId(), member.getEnrollmentId(), member.getCourseId(),
                member.getOriginalTuitionFee());
    }
}
