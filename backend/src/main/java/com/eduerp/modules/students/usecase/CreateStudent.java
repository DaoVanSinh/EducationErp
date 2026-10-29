package com.eduerp.modules.students.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.StudentProfileNotFoundException;
import com.eduerp.modules.students.StudentsEvents;
import com.eduerp.modules.students.dto.CreateStudentRequest;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo học viên một bước (thay cho "tạo tài khoản ở màn Tài khoản rồi mới tạo hồ sơ"): mời tài khoản với
 * vai trò Học viên của hệ thống, hồ sơ được {@code StudentRoleParticipant} sinh ngay trong cùng
 * transaction khi vai trò được gán, rồi ở đây điền thông tin chi tiết. Lỗi ở bất kỳ bước nào (email
 * trùng, chi nhánh sai) huỷ cả hai - không còn tài khoản học viên thiếu hồ sơ.
 */
@Service
public class CreateStudent {

    private final IdentityManagement identity;
    private final AccessManagement access;
    private final StudentProfileRepository profiles;
    private final ApplicationEventPublisher events;

    CreateStudent(IdentityManagement identity, AccessManagement access, StudentProfileRepository profiles,
            ApplicationEventPublisher events) {
        this.identity = identity;
        this.access = access;
        this.profiles = profiles;
        this.events = events;
    }

    @Transactional
    public UUID execute(UUID actorAccountId, UUID actorBranchId, CreateStudentRequest request) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.STUDENT,
                AccessConstants.Actions.CREATE).branchFilter(request.homeBranchId());
        var accountId = identity.inviteAccount(request.email(), request.fullName().strip(), branchId,
                access.roleIdOf(AccessConstants.RoleCodes.STUDENT), actorAccountId, actorBranchId);

        var profile = profiles.findByAccountId(accountId)
                .orElseThrow(() -> new StudentProfileNotFoundException(accountId));
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setPhone(request.phone());
        profile.setSourceChannel(request.sourceChannel());

        events.publishEvent(new StudentsEvents.StudentProfileCreated(profile.getId(), actorAccountId, actorBranchId));
        return profile.getId();
    }
}
