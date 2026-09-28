package com.eduerp.modules.access;

public final class AccessConstants {

    private AccessConstants() {
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

    /**
     * Tên tài nguyên xuất hiện trong bảng {@code permissions} — đúng vốn từ vựng mà
     * {@code ScopedPermissionEvaluator} đọc lại từ authority {@code PERM:<resource>:<action>:<scope>}.
     * Module khác chỉ tham chiếu chuỗi này khi cần xây biểu thức {@code @PreAuthorize} của riêng
     * chúng, không bao giờ định nghĩa lại một bộ tên tài nguyên thứ hai.
     */
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
     * nơi không gọi được {@code PermissionScope.name()}.
     */
    public static final class ScopeNames {
        private ScopeNames() {
        }

        public static final String ORGANIZATION = "ORGANIZATION";
    }

    /**
     * Namespace Redis mà module này sở hữu — việc lắp key nằm ở {@code integrations.cache.CacheKeyBuilder}.
     */
    public static final class CacheNamespaces {
        private CacheNamespaces() {
        }

        public static final String EFFECTIVE_PERMISSIONS = "perm:account";
    }

    /**
     * Biểu thức {@code @PreAuthorize} của từng nhóm endpoint trong toàn hệ thống. Ghép từ
     * {@link Resources}/{@link Actions} nên tất cả đều là hằng biên dịch, dùng được trong annotation
     * — và tên tài nguyên chỉ tồn tại một chỗ thay vì lặp lại dưới dạng chuỗi rời trong mỗi controller.
     */
    public static final class AccessRules {
        private AccessRules() {
        }

        private static final String CHECK_PREFIX = "hasPermission(null, '";
        private static final String CHECK_SEPARATOR = "', '";
        private static final String CHECK_SUFFIX = "')";
        /**
         * Ngăn giữa action và scope tối thiểu. Thiếu phần này thì một quyền scope PERSONAL cũng qua
         * được cửa: TEACHER được cấp ACCOUNT:UPDATE:PERSONAL để tự sửa hồ sơ, mà endpoint đổi
         * group/chi nhánh của người khác lại cùng resource+action.
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
}
