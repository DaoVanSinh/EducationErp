package com.eduerp.modules.courses.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.ClassResponse;
import com.eduerp.modules.courses.internal.ClassResponseAssembler;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.courses.internal.repository.ClassSpecifications;
import com.eduerp.shared.PageResponse;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Danh sách lớp cho màn quản trị. Scope BRANCH luôn bị ép về chi nhánh chủ quản (spec 6.4, lớp 2);
 * {@code openOnly} chỉ lấy lớp còn nhận ghi danh (đang tuyển sinh/đang học) - cho ô chọn lớp khi ghi danh.
 */
@Service
public class ListClasses {

    private static final List<CoursesConstants.ClassStatus> OPEN_STATUSES = Arrays.stream(
            CoursesConstants.ClassStatus.values()).filter(CoursesConstants.ClassStatus::isOpen).toList();

    private final ClassRepository classes;
    private final AccessManagement access;
    private final ClassResponseAssembler assembler;

    ListClasses(ClassRepository classes, AccessManagement access, ClassResponseAssembler assembler) {
        this.classes = classes;
        this.access = access;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassResponse> execute(UUID actorAccountId, Pageable pageable, UUID teacherId,
            UUID requestedBranchId, boolean openOnly) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.CLASS,
                AccessConstants.Actions.READ).branchFilter(requestedBranchId);
        var page = classes.findAll(ClassSpecifications.filtered(branchId, teacherId,
                openOnly ? OPEN_STATUSES : List.of()), pageable);
        return assembler.toPage(page);
    }
}
