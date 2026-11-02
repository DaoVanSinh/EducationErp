package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.dto.GroupRegistrationResponse;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.model.GroupRegistration;
import com.eduerp.modules.billing.internal.model.GroupRegistrationMember;
import com.eduerp.modules.billing.internal.repository.GroupRegistrationRepository;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.students.StudentsManagement;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filter optional dồn vào query ở repository; thành viên nạp gộp nhờ {@code @BatchSize} (mirror ListCombos).
 * Tên khoá và tên học viên tra một lần cho cả trang qua facade, không tra riêng từng nhóm.
 */
@Service
public class ListGroupRegistrations {

    private final GroupRegistrationRepository groups;
    private final BillingBranchGuard branchGuard;
    private final CoursesManagement courses;
    private final StudentsManagement students;

    ListGroupRegistrations(GroupRegistrationRepository groups, BillingBranchGuard branchGuard,
            CoursesManagement courses, StudentsManagement students) {
        this.groups = groups;
        this.branchGuard = branchGuard;
        this.courses = courses;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public PageResponse<GroupRegistrationResponse> execute(UUID actorAccountId, Pageable pageable, UUID courseId) {
        var page = groups.search(courseId, branchGuard.listFilter(actorAccountId), pageable);
        var courseNames = courses.courseNamesOf(page.stream().map(GroupRegistration::getCourseId).distinct().toList());
        var studentNames = students.displayNamesOf(page.stream()
                .flatMap(group -> group.getMembers().stream())
                .map(GroupRegistrationMember::getStudentProfileId)
                .distinct().toList());
        return PageResponse.of(page.map(group -> CreateGroupRegistration.toResponse(group,
                courseNames.get(group.getCourseId()), studentNames)));
    }
}
