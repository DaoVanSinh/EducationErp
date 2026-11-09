package com.eduerp.modules.identity;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Cổng cho module nghiệp vụ tham gia vào vòng đời tài khoản mà identity không phải import chúng - cùng kỹ thuật
 * với {@code RoleAssignmentParticipant}: courses chặn khi còn lớp đang mở, enrollment chặn khi còn đang học, billing
 * chặn khi còn nợ, teachers và students khép hồ sơ. Mọi hook chạy đồng bộ trong transaction của thao tác: ném lỗi là
 * huỷ toàn bộ.
 */
public interface AccountLifecycleParticipant {

    /** Trước khi đóng tài khoản. Ném {@code AppException} để chặn. */
    default void beforeClose(UUID accountId) {
    }

    /** Sau khi đóng: {@code closedOn} là ngày làm việc cuối nếu người đó chưa nghỉ trước đó. */
    default void afterClose(UUID accountId, LocalDate closedOn) {
    }

    /** Trước khi chuyển chi nhánh chủ quản. Ném {@code AppException} để chặn. */
    default void beforeBranchTransfer(UUID accountId, UUID newBranchId) {
    }
}
