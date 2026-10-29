package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.internal.ClassResponseAssembler;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng giảng dạy: lớp mà CHÍNH giáo viên đang đăng nhập được phân công. Tách khỏi {@link ListClasses}: ở
 * đây không hỏi quyền CLASS (giáo viên không có), {@code teacherId} luôn là người gọi.
 */
@Service
public class ListMyTeachingClasses {

    private final ClassRepository classes;
    private final ClassResponseAssembler assembler;

    ListMyTeachingClasses(ClassRepository classes, ClassResponseAssembler assembler) {
        this.classes = classes;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> execute(UUID teacherAccountId, Pageable pageable) {
        return assembler.toPage(classes.findAllByTeacherId(teacherAccountId, pageable));
    }
}
