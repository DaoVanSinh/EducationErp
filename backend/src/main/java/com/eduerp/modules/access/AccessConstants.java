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
        public static final String COURSE = "COURSE";
        public static final String CLASS = "CLASS";
        public static final String TEACHER = "TEACHER";
        public static final String STUDENT = "STUDENT";
        public static final String PAYROLL = "PAYROLL";
        public static final String ENROLLMENT = "ENROLLMENT";
        public static final String INVOICE = "INVOICE";
        public static final String GRADE = "GRADE";
        /** Lớp mà CHÍNH giáo viên đang đăng nhập được phân công dạy - cổng giảng dạy. */
        public static final String TEACHING = "TEACHING";
        /** Lớp/điểm/học phí của CHÍNH học viên đang đăng nhập - cổng học tập. */
        public static final String LEARNING = "LEARNING";
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
        public static final String CREATE_ACCOUNT = CHECK_PREFIX + Resources.ACCOUNT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Danh mục RBAC đi kèm nhau, nên một quyền đọc role là đủ cho cả màn hình tham chiếu. */
        public static final String READ_ROLE = CHECK_PREFIX + Resources.ROLE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Số liệu trên dashboard là của toàn hệ thống, nên quyền đọc nó cũng phải ở cấp tổ chức. */
        public static final String READ_DASHBOARD = CHECK_PREFIX + Resources.DASHBOARD + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_BRANCH = CHECK_PREFIX + Resources.BRANCH + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_BRANCH = CHECK_PREFIX + Resources.BRANCH + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_BRANCH = CHECK_PREFIX + Resources.BRANCH + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_COURSE = CHECK_PREFIX + Resources.COURSE + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_CLASS = CHECK_PREFIX + Resources.CLASS + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_TEACHER = CHECK_PREFIX + Resources.TEACHER + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_STUDENT = CHECK_PREFIX + Resources.STUDENT + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String UPDATE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String APPROVE_PAYROLL = CHECK_PREFIX + Resources.PAYROLL + CHECK_SEPARATOR
                + Actions.APPROVE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Rút/hoàn tất ghi danh là chuyển state, dùng UPDATE - hệ thống chưa dùng DELETE ở đâu. */
        public static final String UPDATE_ENROLLMENT = CHECK_PREFIX + Resources.ENROLLMENT + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String CREATE_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.CREATE + MINIMUM_SCOPE + CHECK_SUFFIX;
        public static final String READ_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.READ + MINIMUM_SCOPE + CHECK_SUFFIX;
        /** Thu tiền/huỷ hoá đơn cũng là chuyển state của Invoice, dùng UPDATE (spec mục 5). */
        public static final String UPDATE_INVOICE = CHECK_PREFIX + Resources.INVOICE + CHECK_SEPARATOR
                + Actions.UPDATE + MINIMUM_SCOPE + CHECK_SUFFIX;

        /**
         * Ba rule DUY NHẤT trong hệ thống KHÔNG có {@link #MINIMUM_SCOPE}, và đó là chủ đích (spec
         * mục 6 bước 1): giáo viên chỉ được cấp {@code GRADE:*:PERSONAL}, thêm marker
         * {@code @ORGANIZATION} vào đây là chặn họ ngay ở cửa và Phân hệ 4 chỉ còn admin dùng được.
         * Câu hỏi "lớp này có phải lớp của anh không" mà annotation không trả lời được nằm ở
         * {@code modules.academic.internal.access.ClassGradeAccessGuard}.
         */
        public static final String READ_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.READ + CHECK_SUFFIX;
        public static final String UPDATE_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.UPDATE + CHECK_SUFFIX;
        /**
         * Chưa có endpoint nào gác bằng rule này ở Phân hệ 4: nhập điểm là upsert nên đi theo
         * {@link #UPDATE_GRADE}. Hằng vẫn tồn tại vì spec mục 8 yêu cầu seed {@code (GRADE, CREATE)}
         * cho nhóm admin - có hằng thì quyền đã seed luôn có một tên biên dịch được đối chiếu
         * ({@code AcademicRbacSeedIT}), thay vì chỉ là một chuỗi rời trong file SQL.
         */
        public static final String CREATE_GRADE = CHECK_PREFIX + Resources.GRADE + CHECK_SEPARATOR
                + Actions.CREATE + CHECK_SUFFIX;

        /**
         * Hai cổng tự phục vụ theo vai trò (đề cương ĐATN: RBAC rõ ràng cho Admin/Teacher/Student).
         * Không có {@link #MINIMUM_SCOPE}: dữ liệu trả về LUÔN lọc theo chính {@code principal}, không
         * bao giờ nhận id người khác từ client, nên scope PERSONAL là đủ. Vẫn gác bằng permission thay
         * vì để trống như {@code /api/account/me}: có permission thì frontend biết hiện menu nào cho ai,
         * và admin bật/tắt được cổng cho từng role qua màn hình phân quyền.
         */
        public static final String READ_TEACHING = CHECK_PREFIX + Resources.TEACHING + CHECK_SEPARATOR
                + Actions.READ + CHECK_SUFFIX;
        public static final String READ_LEARNING = CHECK_PREFIX + Resources.LEARNING + CHECK_SEPARATOR
                + Actions.READ + CHECK_SUFFIX;
    }
}
