package com.eduerp.modules.teachers.internal.access;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.EffectivePermission;
import com.eduerp.modules.access.RoleAssignmentParticipant;
import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Hồ sơ giáo viên đi theo NĂNG LỰC giảng dạy (cổng giảng dạy {@code TEACHING:READ}), không theo mã vai
 * trò - cùng luật với {@code StudentRoleParticipant} của students: có năng lực thì có hồ sơ, mất năng lực
 * thì hồ sơ "ngừng hoạt động" (không xoá - còn hợp đồng, phiếu lương, lịch sử dạy).
 */
@Component
class TeacherRoleParticipant implements RoleAssignmentParticipant {

    private final TeacherProfileRepository profiles;

    TeacherRoleParticipant(TeacherProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Override
    public void afterRoleAssigned(UUID accountId, Set<EffectivePermission> granted) {
        boolean teacher = EffectivePermission.grants(granted, AccessConstants.Resources.TEACHING,
                AccessConstants.Actions.READ);
        var existing = profiles.findByAccountId(accountId);
        if (teacher && existing.isEmpty()) {
            profiles.save(new TeacherProfile(accountId, List.of(), null, null));
            return;
        }
        existing.ifPresent(profile -> profile.setActive(teacher));
    }
}
