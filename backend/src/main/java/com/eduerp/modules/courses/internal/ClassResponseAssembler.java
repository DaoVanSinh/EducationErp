package com.eduerp.modules.courses.internal;

import com.eduerp.modules.courses.ClassRosterPort;
import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Dựng {@link ClassResponse} cho một trang lớp - dùng chung cho màn quản trị và cổng giảng dạy. Tên khóa
 * học lấy thẳng qua {@code Class.course} (cùng module); tên chi nhánh, tên giáo viên và sĩ số đang học
 * nạp theo LÔ qua facade/cổng của module khác, không bao giờ một truy vấn mỗi lớp (rule #3).
 */
@Component
public class ClassResponseAssembler {

    private final OrganizationManagement organization;
    private final IdentityManagement identity;
    private final ClassRosterPort roster;

    ClassResponseAssembler(OrganizationManagement organization, IdentityManagement identity, ClassRosterPort roster) {
        this.organization = organization;
        this.identity = identity;
        this.roster = roster;
    }

    public PageResponse<ClassResponse> toPage(Page<Class> page) {
        var classIds = page.getContent().stream().map(Class::getId).toList();
        var branchNames = organization.namesOf(page.getContent().stream().map(Class::getBranchId).toList());
        var teachers = identity.summariesOf(page.getContent().stream().map(Class::getTeacherId).toList());
        var enrolled = classIds.isEmpty() ? Map.<UUID, Long>of() : roster.countActiveByClass(classIds);
        return PageResponse.of(page.map(cls -> toResponse(cls, branchNames, teachers, enrolled)));
    }

    private static ClassResponse toResponse(Class cls, Map<UUID, String> branchNames,
            Map<UUID, IdentityManagement.AccountBasicInfo> teachers, Map<UUID, Long> enrolled) {
        var teacher = teachers.get(cls.getTeacherId());
        var slots = cls.getSchedule().stream()
                .map(s -> new WeeklyScheduleSlot(s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList();
        return new ClassResponse(cls.getId(), cls.getCode(), cls.getCourse().getId(), cls.getCourse().getName(),
                cls.getBranchId(), branchNames.get(cls.getBranchId()), cls.getTeacherId(),
                teacher == null ? null : teacher.fullName(), cls.getMaxSeats(), cls.getMinSeats(),
                enrolled.getOrDefault(cls.getId(), 0L), cls.getStatus(), cls.getStartDate(), cls.getEndDate(),
                cls.getStartedAt(), cls.getEndedAt(), slots);
    }
}
