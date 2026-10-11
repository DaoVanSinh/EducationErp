package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #4: caller có quyền {@code GRADE:*} ở scope {@code PERSONAL}/{@code BRANCH} nhưng
 * KHÔNG phải giáo viên được phân công dạy lớp này (spec mục 6 bước 4). 403 chứ không 404: cả người
 * và lớp đều tồn tại, chỉ là không phải lớp của họ - trả 404 sẽ che mất lý do thật.
 */
public final class NotAssignedToClassException extends AcademicException {

    public NotAssignedToClassException(UUID actorAccountId, UUID classId) {
        super("ACADEMIC_NOT_ASSIGNED_TO_CLASS", HttpStatus.FORBIDDEN,
                "Tài khoản " + actorAccountId + " không được phân công dạy lớp " + classId);
    }
}
