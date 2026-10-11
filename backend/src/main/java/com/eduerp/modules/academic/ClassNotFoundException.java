package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotFoundException extends AcademicException {

    public ClassNotFoundException(UUID classId) {
        super("ACADEMIC_CLASS_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy lớp học " + classId);
    }
}
