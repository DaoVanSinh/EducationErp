package com.eduerp.modules.teachers.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.teachers.TeacherProfileNotFoundException;
import com.eduerp.modules.teachers.dto.TeacherBranchChangeResponse;
import com.eduerp.modules.teachers.internal.access.TeacherBranchGuard;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Giáo viên từng ở những chi nhánh nào, mới nhất trước. */
@Service
public class GetTeacherBranchHistory {

    private final TeacherProfileRepository profiles;
    private final TeacherBranchGuard branchGuard;
    private final IdentityManagement identity;
    private final OrganizationManagement organization;

    GetTeacherBranchHistory(TeacherProfileRepository profiles, TeacherBranchGuard branchGuard,
            IdentityManagement identity, OrganizationManagement organization) {
        this.profiles = profiles;
        this.branchGuard = branchGuard;
        this.identity = identity;
        this.organization = organization;
    }

    @Transactional(readOnly = true)
    public List<TeacherBranchChangeResponse> execute(UUID actorAccountId, UUID profileId) {
        var profile = profiles.findById(profileId).orElseThrow(() -> new TeacherProfileNotFoundException(profileId));
        branchGuard.require(actorAccountId, profile, AccessConstants.Actions.READ);
        var changes = identity.branchHistoryOf(profile.getAccountId());
        var names = organization.namesOf(changes.stream()
                .flatMap(change -> Stream.of(change.fromBranchId(), change.toBranchId()))
                .filter(Objects::nonNull).distinct().toList());
        return changes.stream()
                .map(change -> new TeacherBranchChangeResponse(
                        change.fromBranchId() == null ? null : names.get(change.fromBranchId()),
                        names.get(change.toBranchId()), change.changedAt()))
                .toList();
    }
}
