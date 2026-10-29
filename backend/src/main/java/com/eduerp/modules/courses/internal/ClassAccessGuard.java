package com.eduerp.modules.courses.internal;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.ClassNotFoundException;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Nạp một lớp và kiểm lớp 2 (spec 6.4) cho thao tác quản trị trên nó: scope ORGANIZATION mọi lớp, scope
 * BRANCH chỉ lớp thuộc chi nhánh chủ quản của người gọi. Dùng chung cho sửa, khai giảng, kết thúc, huỷ.
 */
@Component
public class ClassAccessGuard {

    private final ClassRepository classes;
    private final AccessManagement access;

    ClassAccessGuard(ClassRepository classes, AccessManagement access) {
        this.classes = classes;
        this.access = access;
    }

    public Class loadForUpdate(UUID actorAccountId, UUID classId) {
        var cls = classes.findById(classId).orElseThrow(() -> new ClassNotFoundException(classId));
        access.dataScopeOf(actorAccountId, AccessConstants.Resources.CLASS, AccessConstants.Actions.UPDATE)
                .requireCovers(cls.getBranchId());
        return cls;
    }
}
