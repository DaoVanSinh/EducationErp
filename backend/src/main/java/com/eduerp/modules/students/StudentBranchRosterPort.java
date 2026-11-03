package com.eduerp.modules.students;

import java.util.List;
import java.util.UUID;

/**
 * Cổng mà students tự khai báo để biết ai đang học ở một chi nhánh mà KHÔNG import {@code enrollment}
 * (enrollment đã phụ thuộc students - cạnh ngược sẽ thành vòng). Module enrollment cung cấp bean implement.
 */
public interface StudentBranchRosterPort {

    /** Hồ sơ học viên đang có ghi danh hoạt động tại một chi nhánh. */
    List<UUID> activeStudentProfileIdsAt(UUID branchId);
}
