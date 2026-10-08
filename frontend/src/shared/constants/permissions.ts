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
  chat: "CHAT",
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
  [RESOURCE.chat]: "Hỏi đáp khóa học",
};

export const ACTION_LABEL: Record<string, string> = {
  [ACTION.create]: "Tạo",
  [ACTION.read]: "Xem",
  [ACTION.update]: "Sửa",
  [ACTION.delete]: "Xoá",
  [ACTION.approve]: "Phê duyệt",
};

/**
 * Bản sao của IdentityConstants.AccessRules: yêu cầu quyền của từng nhóm endpoint. Mọi endpoint quản
 * trị đều đòi scope ORGANIZATION — một quyền ACCOUNT:UPDATE:PERSONAL chỉ đủ để tự sửa hồ sơ mình.
 * Giao diện phải hỏi đúng câu backend hỏi, nếu không sẽ có nút hiện ra rồi bấm vào là 403.
 */
export const ACCESS_RULE = {
  readDashboard: {
    resource: RESOURCE.dashboard,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.organization,
  },
  readAccount: {
    resource: RESOURCE.account,
    action: ACTION.read,
    scope: PERMISSION_SCOPE.organization,
  },
  updateAccount: {
    resource: RESOURCE.account,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readRole: { resource: RESOURCE.role, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createRole: { resource: RESOURCE.role, action: ACTION.create, scope: PERMISSION_SCOPE.organization },
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
  readCourse: { resource: RESOURCE.course, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
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
  readClass: { resource: RESOURCE.class, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createClass: {
    resource: RESOURCE.class,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateClass: {
    resource: RESOURCE.class,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readTeacher: { resource: RESOURCE.teacher, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
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
  readStudent: { resource: RESOURCE.student, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createStudent: {
    resource: RESOURCE.student,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateStudent: {
    resource: RESOURCE.student,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
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
    scope: PERMISSION_SCOPE.organization,
  },
  createEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateEnrollment: {
    resource: RESOURCE.enrollment,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  readInvoice: { resource: RESOURCE.invoice, action: ACTION.read, scope: PERMISSION_SCOPE.organization },
  createInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.create,
    scope: PERMISSION_SCOPE.organization,
  },
  updateInvoice: {
    resource: RESOURCE.invoice,
    action: ACTION.update,
    scope: PERMISSION_SCOPE.organization,
  },
  /**
   * Hai trong ba entry không ghi scope ở file này, và đó là chủ đích: ba rule `*_GRADE` ở backend
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
   * Entry thứ BA trong file không ghi scope, và đó là chủ đích - cùng lý do với hai rule `*Grade`
   * bên trên: `AccessConstants.AccessRules.CREATE_CHAT` ở backend không mang marker
   * `@ORGANIZATION`, nên câu hỏi đúng là "có quyền CHAT:CREATE ở bất kỳ scope nào không". Học viên
   * được seed `CHAT:CREATE:PERSONAL` (V27); ghi `organization` ở đây sẽ ẩn cả trang chat và mục nav
   * khỏi học viên - người dùng duy nhất mà tính năng này được xây cho.
   *
   * Khác hai rule `*Grade`: ở đây KHÔNG có câu hỏi "tài nguyên này có phải của tôi không" nào mà
   * backend phải trả lời tiếp, nên rule này là toàn bộ sự thật về quyền của màn hình chat.
   */
  createChat: { resource: RESOURCE.chat, action: ACTION.create },
} as const;
