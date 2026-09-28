package com.eduerp.modules.identity;

public final class IdentityConstants {

    private IdentityConstants() {
    }

    public enum PermissionScope {
        PERSONAL(0), BRANCH(1), ORGANIZATION(2);

        private final int rank;

        PermissionScope(int rank) {
            this.rank = rank;
        }

        public int rank() {
            return rank;
        }
    }

    public enum AccountStatus {
        ACTIVE, DISABLED
    }

    public static final class Resources {
        private Resources() {
        }

        public static final String ACCOUNT = "ACCOUNT";
        public static final String ROLE = "ROLE";
        public static final String GROUP = "GROUP";
        public static final String PERMISSION = "PERMISSION";
        public static final String PERMISSION_GROUP = "PERMISSION_GROUP";
        public static final String BRANCH = "BRANCH";
        public static final String AUDIT_LOG = "AUDIT_LOG";
        public static final String DASHBOARD = "DASHBOARD";
    }

    public static final class Actions {
        private Actions() {
        }

        public static final String CREATE = "CREATE";
        public static final String READ = "READ";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
        public static final String APPROVE = "APPROVE";
    }

    public static final class RoleCodes {
        private RoleCodes() {
        }

        public static final String ADMIN = "ADMIN";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
    }

    public static final class Authorities {
        private Authorities() {
        }

        public static final String PERMISSION_AUTHORITY_PREFIX = "PERM:";
    }

    /**
     * Tên scope dưới dạng hằng biên dịch, chỉ để ghép được vào biểu thức {@code @PreAuthorize} —
     * nơi không gọi được {@code PermissionScope.name()}. {@code IdentityConstantsTest} chốt việc
     * hai bên không lệch nhau.
     */
    public static final class ScopeNames {
        private ScopeNames() {
        }

        public static final String ORGANIZATION = "ORGANIZATION";
    }

    public static final class TokenTypes {
        private TokenTypes() {
        }

        public static final String ACCESS = "access";
        public static final String REFRESH = "refresh";
    }

    /**
     * Namespace Redis mà module này sở hữu. Tên nghiệp vụ nằm ở đây, việc lắp key nằm ở
     * {@code integrations.cache.CacheKeyBuilder} — nên {@code integrations} không cần biết
     * "pwreset" hay "perm" nghĩa là gì.
     */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }

        public static final String BLACKLISTED_JTI = "blacklist:jti";
        public static final String ACCOUNT_SESSIONS = "sessions:account";
        public static final String EFFECTIVE_PERMISSIONS = "perm:account";
        public static final String PASSWORD_RESET_TOKEN = "pwreset:token";
    }

    public static final class Cookies {
        private Cookies() {
        }

        public static final String ACCESS_TOKEN = "access_token";
        public static final String REFRESH_TOKEN = "refresh_token";
    }

    /**
     * Tên hành động ghi vào audit log. Loại entity dùng lại {@link Resources} — cùng một danh sách
     * tài nguyên, không dựng thêm một bộ từ vựng thứ hai để rồi hai bên lệch nhau.
     */
    public static final class AuditActions {
        private AuditActions() {
        }

        public static final String LOGIN = "LOGIN";
        public static final String ROLE_CREATE = "ROLE_CREATE";
        public static final String PERMISSION_GROUP_CREATE = "PERMISSION_GROUP_CREATE";
        public static final String ACCOUNT_JOIN_GROUP = "ACCOUNT_JOIN_GROUP";
        public static final String ACCOUNT_TRANSFER_BRANCH = "ACCOUNT_TRANSFER_BRANCH";
    }

    /**
     * Biểu thức {@code @PreAuthorize} của từng nhóm endpoint. Ghép từ {@link Resources}/{@link Actions}
     * nên tất cả đều là hằng biên dịch, dùng được trong annotation — và tên tài nguyên chỉ tồn tại
     * một chỗ thay vì lặp lại dưới dạng chuỗi rời trong mỗi controller.
     */
    public static final class AccessRules {
        private AccessRules() {
        }

        private static final String CHECK_PREFIX = "hasPermission(null, '";
        private static final String CHECK_SEPARATOR = "', '";
        private static final String CHECK_SUFFIX = "')";
        /**
         * Ngăn giữa action và scope tối thiểu. Thiếu phần này thì một quyền scope PERSONAL cũng
         * qua được cửa: TEACHER được cấp ACCOUNT:UPDATE:PERSONAL để tự sửa hồ sơ, mà endpoint
         * đổi group/chi nhánh của người khác lại cùng resource+action.
         */
        private static final String MINIMUM_SCOPE = "@" + ScopeNames.ORGANIZATION;

        public static final String CREATE_ROLE = CHECK_PREFIX + Resources.ROLE + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_PERMISSION_GROUP = CHECK_PREFIX + Resources.PERMISSION_GROUP
                + CHECK_SEPARATOR + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_ACCOUNT = CHECK_PREFIX + Resources.ACCOUNT + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_ACCOUNT = CHECK_PREFIX + Resources.ACCOUNT + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Danh mục RBAC đi kèm nhau, nên một quyền đọc role là đủ cho cả màn hình tham chiếu. */
        public static final String READ_ROLE = CHECK_PREFIX + Resources.ROLE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Số liệu trên dashboard là của toàn hệ thống, nên quyền đọc nó cũng phải ở cấp tổ chức. */
        public static final String READ_DASHBOARD = CHECK_PREFIX + Resources.DASHBOARD + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
    }

    /** Giá trị mặc định do chính module quyết định, không phụ thuộc cấu hình triển khai. */
    public static final class Defaults {
        private Defaults() {
        }

        public static final String ADMIN_FULL_NAME = "Quản trị viên mặc định";
    }
}
