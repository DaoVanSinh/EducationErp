package com.eduerp.modules.access;

import com.eduerp.core.exception.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Gốc của mọi lỗi nghiệp vụ do module access phát ra. */
public sealed class AccessException extends AppException
        permits AccessReferenceNotFoundException, AccessException.SystemAdminLocked,
        AccessException.SelfModificationForbidden, AccessException.OutOfBranchScope,
        AccessException.ScopeDenied, AccessException.NoHomeBranch {

    protected AccessException(String errorCode, HttpStatus status, String message) {
        super(errorCode, status, message);
    }

    /** Vai trò ADMIN hoặc nhóm quyền "Toàn quyền hệ thống" - xem {@link AccessConstants.SystemPermissionGroups}. */
    public static final class SystemAdminLocked extends AccessException {
        public SystemAdminLocked() {
            super(AccessConstants.ErrorCodes.SYSTEM_ADMIN_LOCKED, HttpStatus.CONFLICT,
                    "Vai trò Quản trị viên và nhóm quyền Toàn quyền hệ thống được khoá để tránh tự khoá hệ thống");
        }
    }

    /** Tự đổi vai trò/tự khoá chính mình: một cú bấm nhầm là mất quyền quản trị ngay lập tức. */
    public static final class SelfModificationForbidden extends AccessException {
        public SelfModificationForbidden() {
            super(AccessConstants.ErrorCodes.SELF_MODIFICATION_FORBIDDEN, HttpStatus.CONFLICT,
                    "Không thể thực hiện thao tác này trên chính tài khoản của bạn");
        }
    }

    /** Có quyền ở scope BRANCH nhưng bản ghi thuộc chi nhánh khác (spec mục 6.4, lớp 2). */
    public static final class OutOfBranchScope extends AccessException {
        public OutOfBranchScope(UUID targetBranchId) {
            super(AccessConstants.ErrorCodes.OUT_OF_BRANCH_SCOPE, HttpStatus.FORBIDDEN,
                    "Dữ liệu thuộc chi nhánh " + targetBranchId + " nằm ngoài phạm vi chi nhánh của bạn");
        }
    }

    /** Không có quyền ở scope đủ rộng (chỉ PERSONAL) cho một thao tác quản trị. */
    public static final class ScopeDenied extends AccessException {
        public ScopeDenied(String resource, String action) {
            super(AccessConstants.ErrorCodes.SCOPE_DENIED, HttpStatus.FORBIDDEN,
                    "Không đủ phạm vi quyền cho " + resource + ":" + action);
        }
    }

    /** Scope BRANCH mà tài khoản không thuộc chi nhánh nào - không có dữ liệu nào để xem. */
    public static final class NoHomeBranch extends AccessException {
        public NoHomeBranch() {
            super(AccessConstants.ErrorCodes.NO_HOME_BRANCH, HttpStatus.FORBIDDEN,
                    "Quyền của bạn giới hạn theo chi nhánh nhưng tài khoản chưa thuộc chi nhánh nào");
        }
    }
}
