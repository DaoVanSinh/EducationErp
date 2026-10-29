/** Bản sao của IdentityConstants.Resources ở backend. */
export const RESOURCE = {
  account: "ACCOUNT",
  role: "ROLE",
  group: "GROUP",
  permission: "PERMISSION",
  permissionGroup: "PERMISSION_GROUP",
  branch: "BRANCH",
  course: "COURSE",
  class: "CLASS",
  auditLog: "AUDIT_LOG",
  dashboard: "DASHBOARD",
  teacher: "TEACHER",
  student: "STUDENT",
  payroll: "PAYROLL",
  enrollment: "ENROLLMENT",
  invoice: "INVOICE",
  grade: "GRADE",
  teaching: "TEACHING",
  learning: "LEARNING",
  attendance: "ATTENDANCE",
  assignment: "ASSIGNMENT",
  practice: "PRACTICE",
} as const;

/** Bản sao của IdentityConstants.Actions. */
export const ACTION = {
  create: "CREATE",
  read: "READ",
  update: "UPDATE",
  delete: "DELETE",
  approve: "APPROVE",
} as const;

export const PERMISSION_SCOPE = {
  personal: "PERSONAL",
  branch: "BRANCH",
  organization: "ORGANIZATION",
} as const;

export type PermissionScope = (typeof PERMISSION_SCOPE)[keyof typeof PERMISSION_SCOPE];

/**
 * Thứ bậc scope, khớp PermissionScope.rank() ở backend: quyền scope rộng hơn thoả mãn được yêu cầu
 * hẹp hơn. Sai thứ tự ở đây thì giao diện sẽ mở nút mà API từ chối.
 */
export const PERMISSION_SCOPE_RANK: Record<PermissionScope, number> = {
  [PERMISSION_SCOPE.personal]: 0,
  [PERMISSION_SCOPE.branch]: 1,
  [PERMISSION_SCOPE.organization]: 2,
};

export const PERMISSION_SCOPE_LABEL: Record<PermissionScope, string> = {
  [PERMISSION_SCOPE.personal]: "Cá nhân",
  [PERMISSION_SCOPE.branch]: "Chi nhánh",
  [PERMISSION_SCOPE.organization]: "Toàn tổ chức",
};

export const RESOURCE_LABEL: Record<string, string> = {
  [RESOURCE.account]: "Tài khoản",
  [RESOURCE.role]: "Vai trò",
  [RESOURCE.group]: "Nhóm người dùng",
  [RESOURCE.permission]: "Quyền",
  [RESOURCE.permissionGroup]: "Nhóm quyền",
  [RESOURCE.branch]: "Chi nhánh",
  [RESOURCE.course]: "Khóa học",
  [RESOURCE.class]: "Lớp học",
  [RESOURCE.auditLog]: "Nhật ký",
  [RESOURCE.dashboard]: "Tổng quan",
  [RESOURCE.teacher]: "Giáo viên",
  [RESOURCE.student]: "Học viên",
  [RESOURCE.payroll]: "Lương & Hợp đồng",
  [RESOURCE.enrollment]: "Ghi danh",
  [RESOURCE.invoice]: "Hoá đơn học phí",
  [RESOURCE.grade]: "Điểm & Tiến trình học tập",
  [RESOURCE.teaching]: "Cổng giảng dạy (lớp của tôi)",
  [RESOURCE.learning]: "Cổng học tập (lớp, điểm, học phí của tôi)",
  [RESOURCE.attendance]: "Điểm danh",
  [RESOURCE.assignment]: "Bài tập, mini-test & mini-task",
  [RESOURCE.practice]: "Tự luyện tập (làm mini-task)",
};

export const ACTION_LABEL: Record<string, string> = {
  [ACTION.create]: "Tạo",
  [ACTION.read]: "Xem",
  [ACTION.update]: "Sửa",
  [ACTION.delete]: "Xoá",
  [ACTION.approve]: "Phê duyệt",
};

/**
 * Bản sao của AccessConstants.AccessRules: yêu cầu quyền của từng nhóm endpoint. Cấu hình hệ thống (vai trò,
 * nhóm quyền, chi nhánh, danh mục khoá học, lương, tạo/sửa tài khoản) đòi scope ORGANIZATION; thao tác vận
 * hành theo chi nhánh (lớp, học viên, ghi danh, học phí, xem tài khoản, tổng quan) chấp nhận BRANCH - backend
 * tự lọc về chi nhánh chủ quản. Một quyền ACCOUNT:UPDATE:PERSONAL chỉ đủ để tự sửa hồ sơ mình.
 * Giao diện phải hỏi đúng câu backend hỏi, nếu không sẽ có nút hiện ra rồi bấm vào là 403.
 */
export const ACCESS_RULE = {
  readDashboard: {
    resource: RESOURCE.dashboard,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.branch,
  },
  readAccount: {
    resource: RESOURCE.account,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.branch,
  },
  updateAccount: {
    resource: RESOURCE.account,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readRole: { resource: RESOURCE.role, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createRole: { resource: RESOURCE.role, action: ACTION.create, scope: PERMISSION_SCOPE.organization },
  updateRole: { resource: RESOURCE.role, action: ACTION.update, scope: PERMISSION_SCOPE.organization },
  updatePermissionGroup: {
    resource: RESOURCE.permissionGroup,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  createPermissionGroup: {
    resource: RESOURCE.permissionGroup,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  readBranch: { resource: RESOURCE.branch, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createBranch: {
    resource: RESOURCE.branch,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateBranch: {
    resource: RESOURCE.branch,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readCourse: { resource: RESOURCE.course, action: ACTION.read, scope: PERMISSION_SCOPE.branch },
  createCourse: {
    resource: RESOURCE.course,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateCourse: {
    resource: RESOURCE.course,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readClass: { resource: RESOURCE.class, action: ACTION.read, scope: PERMISSION_SCOPE.branch },
  createClass: {
    resource: RESOURCE.class,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.branch,
  },
  updateClass: {
    resource: RESOURCE.class,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.branch,
  },
  readTeacher: { resource: RESOURCE.teacher, action: ACTION.read, scope: PERMISSION_SCOPE.branch },
  createTeacher: {
    resource: RESOURCE.teacher,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateTeacher: {
    resource: RESOURCE.teacher,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readStudent: { resource: RESOURCE.student, action: ACTION.read, scope: PERMISSION_SCOPE.branch },
  createStudent: {
    resource: RESOURCE.student,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.branch,
  },
  updateStudent: {
    resource: RESOURCE.student,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.branch,
  },
  createAccount: {
    resource: RESOURCE.account,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  readPayroll: { resource: RESOURCE.payroll, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createPayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updatePayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  approvePayroll: {
    resource: RESOURCE.payroll,
    action: ACTION.approve,
    scope: PERMISSION_SCOPE.organization,
  },
  readEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.branch,
  },
  createEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.branch,
  },
  updateEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.branch,
  },
  /** Bậc giảm giá combo là cấu hình giá toàn trung tâm - chỉ ORGANIZATION (AccessRules.*_COMBO_DISCOUNT_TIER). */
  createComboDiscountTier: {
    resource: RESOURCE.invoice,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateComboDiscountTier: {
    resource: RESOURCE.invoice,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readInvoice: { resource: RESOURCE.invoice, action: ACTION.read, scope: PERMISSION_SCOPE.branch },
  createInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.branch,
  },
  updateInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.branch,
  },
  /**
   * Hai entry DUY NHẤT trong file không ghi scope, và đó là chủ đích: ba rule `*_GRADE` ở backend
   * không có marker `@ORGANIZATION` (AccessConstants.AccessRules.READ_GRADE), nên câu hỏi đúng là
   * "có quyền GRADE ở bất kỳ scope nào không". Ghi `organization` ở đây sẽ ẩn màn hình sổ điểm khỏi
   * giáo viên — những người duy nhất thực sự nhập điểm.
   *
   * Giao diện chỉ dùng hai rule này để quyết định HIỂN THỊ. Nguồn sự thật cho "lớp này có phải lớp
   * của tôi không" là lỗi 403 ACADEMIC_NOT_ASSIGNED_TO_CLASS từ backend (spec mục 10) — frontend
   * không có dữ liệu để tự kết luận điều đó và không được thử.
   */
  readGrade: { resource: RESOURCE.grade, action: ACTION.read },
  updateGrade: { resource: RESOURCE.grade, action: ACTION.update },
  /**
   * Cổng theo vai trò (AccessConstants.AccessRules.READ_TEACHING/READ_LEARNING): cũng không ghi
   * scope vì backend không đòi - dữ liệu luôn lọc theo chính người đăng nhập. TEACHER chỉ có
   * readTeaching, STUDENT chỉ có readLearning, ADMIN không có cái nào.
   */
  readTeaching: { resource: RESOURCE.teaching, action: ACTION.read },
  readLearning: { resource: RESOURCE.learning, action: ACTION.read },
  /**
   * Điểm danh, bài tập/mini-test, mini-task: không ghi scope vì backend không đòi - giáo viên chỉ có
   * scope PERSONAL. Nguồn sự thật cho "lớp này có phải lớp của tôi không" là lỗi 403
   * *_NOT_ASSIGNED_TO_CLASS từ backend, giống sổ điểm.
   */
  readAttendance: { resource: RESOURCE.attendance, action: ACTION.read },
  updateAttendance: { resource: RESOURCE.attendance, action: ACTION.update },
  readAssignment: { resource: RESOURCE.assignment, action: ACTION.read },
  createAssignment: { resource: RESOURCE.assignment, action: ACTION.create },
  updateAssignment: { resource: RESOURCE.assignment, action: ACTION.update },
  /** Học viên nộp một lượt làm mini-task của chính mình (PRACTICE:CREATE). */
  createPractice: { resource: RESOURCE.practice, action: ACTION.create },
} as const;
