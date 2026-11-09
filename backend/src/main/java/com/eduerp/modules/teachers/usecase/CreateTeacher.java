package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.TeachersEvents;
import com.eduerp.modules.teachers.dto.CreateTeacherRequest;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import com.eduerp.modules.teachers.internal.rules.TeacherPayRules;
import com.eduerp.modules.teachers.internal.util.TeacherPayMapper;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo giáo viên một bước: mời tài khoản với vai trò Giáo viên của hệ thống, hồ sơ được
 * {@code TeacherRoleParticipant} sinh trong cùng transaction, rồi điền môn dạy, liên hệ và điều khoản lương ở
 * đây. Lỗi ở bất kỳ bước nào huỷ tất cả - không còn tài khoản giáo viên thiếu hồ sơ hay thiếu lương.
 */
@Service
public class CreateTeacher {

    private final IdentityManagement identity;
    private final AccessManagement access;
    private final TeacherProfileRepository profiles;
    private final ApplicationEventPublisher events;

    CreateTeacher(IdentityManagement identity, AccessManagement access, TeacherProfileRepository profiles,
            ApplicationEventPublisher events) {
        this.identity = identity;
        this.access = access;
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateTeacherRequest request) {
        TeacherPayRules.requireValid(request.payTerms());
        // Admin chi nhánh chỉ tạo giáo viên cho chi nhánh mình; admin gốc tạo cho chi nhánh nào cũng được.
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.TEACHER, AccessConstants.Actions.CREATE)
                .requireCovers(request.homeBranchId());
        var accountId = identity.inviteAccount(request.email(), request.fullName().strip(), request.homeBranchId(),
                access.roleIdOf(AccessConstants.RoleCodes.TEACHER), actorAccountId, actorBranchId);

        var profile = profiles.findByAccountId(accountId)
                .orElseThrow(() -> new TeacherProfileNotFoundException(accountId));
        profile.setSubjects(request.subjects());
        profile.setPhone(request.phone());
        profile.setBio(request.bio());
        TeacherPayMapper.apply(profile, request.payTerms());

        events.publishEvent(new TeachersEvents.TeacherProfileCreated(profile.getId(), actorAccountId, actorBranchId));
        return profile.getId();
    }
}
