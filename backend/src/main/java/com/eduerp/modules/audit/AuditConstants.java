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
        public static final String BRANCH_CREATE = "BRANCH_CREATE";
        public static final String BRANCH_UPDATE = "BRANCH_UPDATE";
        public static final String COURSE_CREATE = "COURSE_CREATE";
        public static final String COURSE_UPDATE = "COURSE_UPDATE";
        public static final String CLASS_CREATE = "CLASS_CREATE";
        public static final String CLASS_UPDATE = "CLASS_UPDATE";
        public static final String ACCOUNT_CREATE = "ACCOUNT_CREATE";
        public static final String ACCOUNT_INVITE_RESEND = "ACCOUNT_INVITE_RESEND";
        public static final String ACCOUNT_INVITE_REVOKE = "ACCOUNT_INVITE_REVOKE";
        public static final String CONTRACT_CREATE = "CONTRACT_CREATE";
        public static final String CONTRACT_TERMINATE = "CONTRACT_TERMINATE";
        public static final String PAYROLL_RUN_APPROVE = "PAYROLL_RUN_APPROVE";
    }

    public static final class EntityTypes {
        private EntityTypes() {
        }

        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String PERMISSION_GROUP = "PERMISSION_GROUP";
        public static final String BRANCH = "BRANCH";
        public static final String COURSE = "COURSE";
        public static final String CLASS = "CLASS";
        public static final String CONTRACT = "CONTRACT";
        public static final String PAYROLL_RUN = "PAYROLL_RUN";
    }
}
