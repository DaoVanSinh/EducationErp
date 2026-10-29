package com.eduerp.modules.courses;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Cổng mà courses tự khai báo để hỏi/đóng danh sách học viên của một lớp mà KHÔNG import
 * {@code enrollment} (enrollment đã phụ thuộc courses - cạnh ngược sẽ thành vòng). Module enrollment cung
 * cấp bean implement (Dependency Inversion, cùng kỹ thuật {@code access.AccountExistenceCheck}).
 *
 * <p>Gọi ĐỒNG BỘ trong transaction của use case vòng đời lớp: kết thúc/huỷ lớp và đóng ghi danh là một
 * thao tác nguyên tử - không có lúc lớp đã kết thúc mà học viên vẫn "đang học".
 */
public interface ClassRosterPort {

    /** Số ghi danh đang học (ACTIVE) của một lớp. */
    long countActive(UUID classId);

    /** Như {@link #countActive(UUID)} cho nhiều lớp một lần; lớp không có ai vắng mặt trong map. */
    Map<UUID, Long> countActiveByClass(Collection<UUID> classIds);

    /** Lớp kết thúc: mọi ghi danh đang học chuyển sang hoàn tất. */
    void completeActiveEnrollments(UUID classId, UUID actorAccountId, UUID actorBranchId);

    /** Lớp bị huỷ: mọi ghi danh đang học chuyển sang đã rút. */
    void withdrawActiveEnrollments(UUID classId, UUID actorAccountId, UUID actorBranchId);
}
