package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Nhập điểm cho lớp chưa khai giảng hoặc đã huỷ - lớp chưa có buổi học nào để có điểm. */
public final class ClassNotStartedException extends AcademicException {
    public ClassNotStartedException(UUID classId) {
        super("ACADEMIC_CLASS_NOT_STARTED", HttpStatus.CONFLICT,
                "Lớp " + classId + " chưa khai giảng hoặc đã huỷ, chưa nhập điểm được");
    }
}
