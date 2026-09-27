package com.eduerp.identity;

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
}
