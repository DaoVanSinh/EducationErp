package com.eduerp.modules.landing.usecase;

import com.eduerp.modules.billing.BillingManagement;
import com.eduerp.modules.billing.BillingManagement.ActiveComboTierSnapshot;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.CoursesManagement.ActiveClassSnapshot;
import com.eduerp.modules.courses.CoursesManagement.ActiveCourseSnapshot;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.landing.dto.LandingOverviewResponse;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingBranchItem;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingClassItem;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingComboTierItem;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingCourseItem;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingStats;
import com.eduerp.modules.landing.dto.LandingOverviewResponse.LandingTeacherItem;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.organization.OrganizationManagement.BranchPublicSummary;
import com.eduerp.modules.teachers.TeachersManagement;
import com.eduerp.modules.teachers.TeachersManagement.ActiveTeacherSnapshot;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tổng hợp dữ liệu công khai cho Landing Page từ Facade của các module nghiệp vụ và bảng cấu hình
 * trung tâm — tuyệt đối không truy vấn chéo bảng của module khác.
 */
@Service
public class GetLandingOverview {

    private final CoursesManagement courses;
    private final OrganizationManagement organization;
    private final TeachersManagement teachers;
    private final IdentityManagement identity;
    private final BillingManagement billing;
    private final GetLandingContentConfig getContentConfig;

    GetLandingOverview(
            CoursesManagement courses,
            OrganizationManagement organization,
            TeachersManagement teachers,
            IdentityManagement identity,
            BillingManagement billing,
            GetLandingContentConfig getContentConfig) {
        this.courses = courses;
        this.organization = organization;
        this.teachers = teachers;
        this.identity = identity;
        this.billing = billing;
        this.getContentConfig = getContentConfig;
    }

    @Transactional(readOnly = true)
    public LandingOverviewResponse execute() {
        var activeCourses = courses.listActiveCourses();
        var activeClasses = courses.listActiveClasses();
        var activeBranches = organization.listActiveBranches();
        var activeTeachers = teachers.listActiveTeachers();
        var activeTiers = billing.listActiveComboDiscountTiers();

        var accountNames = resolveAccountFullNames(activeClasses, activeTeachers);
        var branchNames = activeBranches.stream()
                .collect(Collectors.toMap(BranchPublicSummary::id, BranchPublicSummary::name));
        var openClassCountByCourse = activeClasses.stream()
                .collect(Collectors.groupingBy(ActiveClassSnapshot::courseId, Collectors.counting()));

        var courseItems = toCourseItems(activeCourses, openClassCountByCourse);
        var classItems = toClassItems(activeClasses, branchNames, accountNames);
        var teacherItems = toTeacherItems(activeTeachers, accountNames);
        var branchItems = toBranchItems(activeBranches);
        var tierItems = toTierItems(activeTiers);

        var maxDiscount = activeTiers.stream()
                .map(ActiveComboTierSnapshot::discountPercent)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        var stats = new LandingStats(
                courseItems.size(),
                classItems.size(),
                teacherItems.size(),
                branchItems.size(),
                maxDiscount);

        var config = getContentConfig.execute();

        return new LandingOverviewResponse(
                stats, courseItems, classItems, teacherItems, branchItems, tierItems, config);
    }

    private Map<UUID, String> resolveAccountFullNames(
            List<ActiveClassSnapshot> activeClasses, List<ActiveTeacherSnapshot> activeTeachers) {
        var accountIds = new HashSet<UUID>();
        for (var cls : activeClasses) {
            accountIds.add(cls.teacherId());
        }
        for (var teacher : activeTeachers) {
            accountIds.add(teacher.accountId());
        }
        return identity.summariesOf(accountIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().fullName()));
    }

    private static List<LandingCourseItem> toCourseItems(
            List<ActiveCourseSnapshot> activeCourses, Map<UUID, Long> openClassCountByCourse) {
        return activeCourses.stream()
                .map(course -> new LandingCourseItem(
                        course.id(),
                        course.code(),
                        course.name(),
                        course.description(),
                        course.standardSessionCount(),
                        course.tuitionFee(),
                        openClassCountByCourse.getOrDefault(course.id(), 0L).intValue(),
                        course.gradeColumns()))
                .toList();
    }

    private static List<LandingClassItem> toClassItems(
            List<ActiveClassSnapshot> activeClasses,
            Map<UUID, String> branchNames,
            Map<UUID, String> accountNames) {
        return activeClasses.stream()
                .map(cls -> new LandingClassItem(
                        cls.id(),
                        cls.code(),
                        cls.courseId(),
                        cls.courseName(),
                        cls.branchId(),
                        branchNames.get(cls.branchId()),
                        accountNames.get(cls.teacherId()),
                        cls.maxSeats(),
                        cls.schedule(),
                        cls.status(),
                        cls.startDate(),
                        (int) Math.max(0, cls.maxSeats() - cls.activeEnrollments())))
                .toList();
    }

    private static List<LandingTeacherItem> toTeacherItems(
            List<ActiveTeacherSnapshot> activeTeachers, Map<UUID, String> accountNames) {
        return activeTeachers.stream()
                .filter(teacher -> accountNames.containsKey(teacher.accountId()))
                .map(teacher -> new LandingTeacherItem(
                        teacher.profileId(),
                        accountNames.get(teacher.accountId()),
                        teacher.subjects(),
                        teacher.bio()))
                .toList();
    }

    private static List<LandingBranchItem> toBranchItems(List<BranchPublicSummary> activeBranches) {
        return activeBranches.stream()
                .map(branch -> new LandingBranchItem(
                        branch.id(),
                        branch.code(),
                        branch.name(),
                        branch.address()))
                .toList();
    }

    private static List<LandingComboTierItem> toTierItems(
            List<ActiveComboTierSnapshot> activeTiers) {
        return activeTiers.stream()
                .map(tier -> new LandingComboTierItem(
                        tier.id(),
                        tier.minCourseCount(),
                        tier.discountPercent()))
                .toList();
    }
}
