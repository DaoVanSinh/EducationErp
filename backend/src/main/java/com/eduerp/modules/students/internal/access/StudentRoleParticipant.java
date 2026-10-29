package com.eduerp.modules.students.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.RoleAssignmentParticipant;
import com.eduerp.modules.students.internal.model.StudentProfile;
import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hồ sơ học viên đi theo NĂNG LỰC học viên (cổng học tập {@code LEARNING:READ}), không theo mã vai trò:
 * tài khoản nào được cấp năng lực này - tạo ở màn Tài khoản, màn Học viên, hay đổi vai trò sau - đều có
 * hồ sơ để ghi danh được; mất năng lực thì hồ sơ chuyển "ngừng hoạt động" (không xoá - còn lịch sử học).
 */
@Component
class StudentRoleParticipant implements RoleAssignmentParticipant {

    private final StudentProfileRepository profiles;

    StudentRoleParticipant(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public void afterRoleAssigned(UUID accountId, Set<EffectivePermission> granted) {
        boolean learner = EffectivePermission.grants(granted, AccessConstants.Resources.LEARNING,
                AccessConstants.Actions.READ);
        var existing = profiles.findByAccountId(accountId);
        if (learner && existing.isEmpty()) {
            profiles.save(new StudentProfile(accountId, null, null, null));
            return;
        }
        existing.ifPresent(profile -> profile.setActive(learner));
    }
}
