package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.shared.PageResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListClasses {

    private final ClassRepository classes;
    private final OrganizationManagement organization;
    private final IdentityManagement identity;

    ListClasses(ClassRepository classes, OrganizationManagement organization, IdentityManagement identity) {
        this.classes = classes;
        this.organization = organization;
        this.identity = identity;
    }

    /**
     * Tên khóa học lấy thẳng qua {@code Class.course} (cùng module - rule #3 không áp dụng). Tên chi
     * nhánh/giáo viên phải nạp theo lô qua facade organization/identity, tránh N+1 - đúng pattern
     * {@code ListAccounts}.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> execute(Pageable pageable) {
        Page<Class> page = classes.findAll(pageable);
        var branchIds = page.getContent().stream().map(Class::getBranchId).toList();
        var teacherIds = page.getContent().stream().map(Class::getTeacherId).toList();

        var branchNames = organization.namesOf(branchIds);
        var teachers = identity.summariesOf(teacherIds);

        return PageResponse.of(page.map(cls -> toResponse(cls, branchNames, teachers)));
    }

    private static ClassResponse toResponse(Class cls, Map<UUID, String> branchNames,
            Map<UUID, IdentityManagement.AccountBasicInfo> teachers) {
        var teacher = teachers.get(cls.getTeacherId());
        var slots = cls.getSchedule().stream()
                .map(s -> new WeeklyScheduleSlot(s.getDayOfWeek(), s.getStartTime(), s.getEndTime()))
                .toList();
        return new ClassResponse(cls.getId(), cls.getCode(), cls.getCourse().getId(),
                cls.getCourse().getName(), cls.getBranchId(),
                branchNames.get(cls.getBranchId()), cls.getTeacherId(),
                teacher == null ? null : teacher.fullName(), cls.getMaxSeats(), cls.isActive(), slots);
    }
}
