package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.ComboDiscountTierNotConfiguredException;
import com.eduerp.modules.billing.CourseNotFoundException;
import com.eduerp.modules.billing.CourseTuitionNotConfiguredException;
import com.eduerp.modules.billing.EnrollmentAlreadyDiscountedException;
import com.eduerp.modules.billing.EnrollmentAlreadyInvoicedException;
import com.eduerp.modules.billing.EnrollmentNotActiveForBillingException;
import com.eduerp.modules.billing.EnrollmentNotFoundException;
import com.eduerp.modules.billing.GroupMemberMismatchException;
import com.eduerp.modules.billing.MinimumGroupSizeException;
import com.eduerp.modules.billing.dto.CreateGroupRegistrationRequest;
import com.eduerp.modules.billing.dto.GroupRegistrationMemberResponse;
import com.eduerp.modules.billing.dto.GroupRegistrationResponse;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.GroupRegistration;
import com.eduerp.modules.billing.internal.pricing.EnrollmentDiscounts;
import com.eduerp.modules.billing.internal.repository.ComboDiscountTierRepository;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.enrollment.EnrollmentManagement.EnrollmentSummaryResponse;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lập nhóm từ các ghi danh ĐANG HỌC đã có sẵn (không tạo ghi danh mới - mirror {@code CreateCombo}).
 *
 * <p>Thứ tự kiểm tra: số lượng → ghi danh tồn tại/đang học → cùng khoá, cùng chi nhánh, mỗi học viên một lần →
 * chưa hưởng ưu đãi khác → chưa có hoá đơn sống → học phí khoá → bậc giảm giá → lưu. Bậc giảm giá dùng chung
 * với combo: "từ N suất học" - N khoá của một người hay N người cùng một khoá.
 */
@Service
public class CreateGroupRegistration {

    private final GroupRegistrationRepository groups;
    private final ComboDiscountTierRepository tiers;
    private final InvoiceRepository invoices;
    private final EnrollmentDiscounts discounts;
    private final EnrollmentManagement enrollmentManagement;
    private final CoursesManagement coursesManagement;
    private final ApplicationEventPublisher events;
    private final BillingBranchGuard branchGuard;

    CreateGroupRegistration(GroupRegistrationRepository groups, ComboDiscountTierRepository tiers,
            InvoiceRepository invoices, EnrollmentDiscounts discounts, EnrollmentManagement enrollmentManagement,
            CoursesManagement coursesManagement, ApplicationEventPublisher events, BillingBranchGuard branchGuard) {
        this.groups = groups;
        this.tiers = tiers;
        this.invoices = invoices;
        this.discounts = discounts;
        this.enrollmentManagement = enrollmentManagement;
        this.coursesManagement = coursesManagement;
        this.events = events;
        this.branchGuard = branchGuard;
    }

    @Transactional
    public GroupRegistrationResponse execute(UUID actorAccountId, UUID actorBranchId,
            CreateGroupRegistrationRequest request) {
        var enrollmentIds = List.copyOf(new LinkedHashSet<>(request.enrollmentIds()));
        if (enrollmentIds.size() < BillingConstants.Limits.MIN_MEMBERS_PER_GROUP) {
            throw new MinimumGroupSizeException(BillingConstants.Limits.MIN_MEMBERS_PER_GROUP);
        }

        var members = enrollmentIds.stream().map(this::loadActiveEnrollment).toList();
        var first = members.get(0);
        branchGuard.require(actorAccountId, AccessConstants.Actions.CREATE, first.branchId());
        requireSameCourseBranchAndDistinctStudents(members, first);
        members.forEach(member -> requireNotDiscountedNorInvoiced(member.enrollmentId(), enrollmentIds));

        var tuitionFee = tuitionOf(first.courseId());
        var tier = tiers.findFirstByActiveTrueAndMinCourseCountLessThanEqualOrderByMinCourseCountDesc(members.size())
                .orElseThrow(() -> new ComboDiscountTierNotConfiguredException(members.size()));

        var group = new GroupRegistration(first.courseId(), first.branchId(), tier.getDiscountPercent(),
                actorAccountId);
        members.forEach(member -> group.addMember(member.enrollmentId(), member.studentProfileId(), tuitionFee));

        var saved = save(group, enrollmentIds);
        events.publishEvent(new BillingEvents.GroupRegistrationCreated(saved.getId(), actorAccountId, actorBranchId));
        return toResponse(saved);
    }

    private EnrollmentSummaryResponse loadActiveEnrollment(UUID enrollmentId) {
        var enrollment = enrollmentManagement.getEnrollment(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.status() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveForBillingException(enrollmentId);
        }
        return enrollment;
    }

    private static void requireSameCourseBranchAndDistinctStudents(List<EnrollmentSummaryResponse> members,
            EnrollmentSummaryResponse first) {
        var seenStudents = new HashSet<UUID>();
        for (var member : members) {
            var sameCourseAndBranch = member.courseId().equals(first.courseId())
                    && member.branchId().equals(first.branchId());
            if (!sameCourseAndBranch || !seenStudents.add(member.studentProfileId())) {
                throw new GroupMemberMismatchException(member.enrollmentId());
            }
        }
    }

    private void requireNotDiscountedNorInvoiced(UUID enrollmentId, List<UUID> allIds) {
        if (discounts.isDiscounted(enrollmentId)) {
            throw new EnrollmentAlreadyDiscountedException(allIds);
        }
        if (invoices.countByEnrollmentIdAndStatusNot(enrollmentId, BillingConstants.InvoiceStatus.CANCELLED) > 0) {
            throw new EnrollmentAlreadyInvoicedException(enrollmentId);
        }
    }

    private BigDecimal tuitionOf(UUID courseId) {
        var tuition = coursesManagement.getCourseTuition(courseId)
                .orElseThrow(() -> new CourseNotFoundException(courseId));
        if (tuition.tuitionFee() == null) {
            throw new CourseTuitionNotConfiguredException(courseId);
        }
        return tuition.tuitionFee();
    }

    /** UNIQUE {@code group_registration_members.enrollment_id} chặn race hai request cùng lập nhóm một ghi danh. */
    private GroupRegistration save(GroupRegistration group, List<UUID> enrollmentIds) {
        try {
            return groups.saveAndFlush(group);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new EnrollmentAlreadyDiscountedException(enrollmentIds);
        }
    }

    /** Một chỗ map duy nhất, dùng lại ở {@code ListGroupRegistrations}. */
    static GroupRegistrationResponse toResponse(GroupRegistration group) {
        var members = group.getMembers().stream()
                .map(member -> new GroupRegistrationMemberResponse(member.getEnrollmentId(),
                        member.getStudentProfileId(), member.getOriginalTuitionFee(),
                        member.getDiscountedTuitionFee()))
                .toList();
        return new GroupRegistrationResponse(group.getId(), group.getCourseId(), group.getBranchId(),
                group.getDiscountPercent(), group.getCreatedAt(), members);
    }
}
