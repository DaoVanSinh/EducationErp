package com.eduerp.modules.audit;

/** Từ vựng của riêng bảng audit_logs. Module publish sự kiện không cần biết các tên này — chỉ audit
 * (nơi map sự kiện sang một dòng log) và dashboard (nơi lọc lại "N lượt đăng nhập gần nhất") cần. */
public final class AuditConstants {

    private AuditConstants() {
    }

    public static final class Actions {
        private Actions() {
        }

        public static final String LOGIN = "LOGIN";
        public static final String ROLE_CREATE = "ROLE_CREATE";
        public static final String PERMISSION_GROUP_CREATE = "PERMISSION_GROUP_CREATE";
        public static final String ACCOUNT_JOIN_GROUP = "ACCOUNT_JOIN_GROUP";
        public static final String ACCOUNT_TRANSFER_BRANCH = "ACCOUNT_TRANSFER_BRANCH";
    }

    public static final class EntityTypes {
        private EntityTypes() {
        }

        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String PERMISSION_GROUP = "PERMISSION_GROUP";
    }
}
