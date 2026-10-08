package com.eduerp.modules.students;

import java.util.UUID;

public final class StudentsEvents {

    private StudentsEvents() {
    }

    public record StudentProfileCreated(UUID studentProfileId, UUID actorAccountId, UUID actorBranchId) {
    }

    public record StudentProfileUpdated(UUID studentProfileId, UUID actorAccountId, UUID actorBranchId) {
    }
}
