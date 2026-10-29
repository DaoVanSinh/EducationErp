/** Đường dẫn API. Một chỗ duy nhất, để đổi endpoint không phải đi tìm chuỗi trong component. */
export const API_ROUTE = {
  auth: {
    login: "/api/auth/login",
    completeInvite: "/api/auth/complete-invite",
    refresh: "/api/auth/refresh",
    logout: "/api/auth/logout",
  },
  account: {
    me: "/api/account/me",
    profile: "/api/account/profile",
    changePassword: "/api/account/change-password",
    forgotPassword: "/api/account/forgot-password",
    resetPassword: "/api/account/reset-password",
  },
  rbac: {
    accounts: "/api/rbac/accounts",
    catalog: "/api/rbac/catalog",
    roles: "/api/rbac/roles",
    role: (roleId: string) => `/api/rbac/roles/${roleId}`,
    permissionGroups: "/api/rbac/permission-groups",
    permissionGroup: (permissionGroupId: string) => `/api/rbac/permission-groups/${permissionGroupId}`,
    accountGroups: (accountId: string) => `/api/rbac/accounts/${accountId}/groups`,
    accountBranch: (accountId: string) => `/api/rbac/accounts/${accountId}/transfer-branch`,
    accountResendInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/resend-invite`,
    accountRevokeInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/revoke-invite`,
    accountRole: (accountId: string) => `/api/rbac/accounts/${accountId}/role`,
    accountDisable: (accountId: string) => `/api/rbac/accounts/${accountId}/disable`,
    accountEnable: (accountId: string) => `/api/rbac/accounts/${accountId}/enable`,
    accountSignOutEverywhere: (accountId: string) => `/api/rbac/accounts/${accountId}/sign-out-everywhere`,
  },
  dashboard: {
    stats: "/api/dashboard/stats",
  },
  organization: {
    branches: "/api/organization/branches",
    branch: (branchId: string) => `/api/organization/branches/${branchId}`,
  },
  courses: {
    courses: "/api/courses/courses",
    course: (courseId: string) => `/api/courses/courses/${courseId}`,
    classes: "/api/courses/classes",
    class: (classId: string) => `/api/courses/classes/${classId}`,
    classStart: (classId: string) => `/api/courses/classes/${classId}/start`,
    classComplete: (classId: string) => `/api/courses/classes/${classId}/complete`,
    classCancel: (classId: string) => `/api/courses/classes/${classId}/cancel`,
    /** Cổng giảng dạy: lớp của CHÍNH giáo viên đang đăng nhập (READ_TEACHING). */
    myClasses: "/api/courses/me/classes",
  },
  teachers: {
    profiles: "/api/teachers/profiles",
    profile: (profileId: string) => `/api/teachers/profiles/${profileId}`,
  },
  students: {
    profiles: "/api/students/profiles",
    profile: (profileId: string) => `/api/students/profiles/${profileId}`,
  },
  payroll: {
    contracts: "/api/payroll/contracts",
    contract: (contractId: string) => `/api/payroll/contracts/${contractId}`,
    contractTerminate: (contractId: string) => `/api/payroll/contracts/${contractId}/terminate`,
    contractFile: (contractId: string) => `/api/payroll/contracts/${contractId}/file`,
    /** Hợp đồng/phiếu lương CỦA CHÍNH người gọi - không cần quyền READ_PAYROLL, dùng cho tab
     * "Lương"/"Hợp đồng" của trang Hồ sơ của tôi. */
    myContracts: "/api/payroll/me/contracts",
    myPayslips: "/api/payroll/me/payslips",
    runs: "/api/payroll/runs",
    run: (runId: string) => `/api/payroll/runs/${runId}`,
    runSubmit: (runId: string) => `/api/payroll/runs/${runId}/submit`,
    runApprove: (runId: string) => `/api/payroll/runs/${runId}/approve`,
    runReject: (runId: string) => `/api/payroll/runs/${runId}/reject`,
    payslip: (runId: string, payslipId: string) => `/api/payroll/runs/${runId}/payslips/${payslipId}`,
  },
  enrollment: {
    enrollments: "/api/enrollment/enrollments",
    enrollment: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}`,
    enrollmentWithdraw: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}/withdraw`,
    enrollmentComplete: (enrollmentId: string) => `/api/enrollment/enrollments/${enrollmentId}/complete`,
  },
  billing: {
    invoices: "/api/billing/invoices",
    invoice: (invoiceId: string) => `/api/billing/invoices/${invoiceId}`,
    invoiceOnlinePayment: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/online-payment`,
    invoiceManualPayment: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/manual-payment`,
    invoiceCancel: (invoiceId: string) => `/api/billing/invoices/${invoiceId}/cancel`,
    combos: "/api/billing/combos",
    combo: (comboId: string) => `/api/billing/combos/${comboId}`,
    comboCancel: (comboId: string) => `/api/billing/combos/${comboId}/cancel`,
    /** comboId nằm trong body, mirror POST /api/billing/invoices vốn nhận enrollmentId trong body. */
    comboInvoices: "/api/billing/combos/invoices",
    comboDiscountTiers: "/api/billing/combo-discount-tiers",
    comboDiscountTier: (tierId: string) => `/api/billing/combo-discount-tiers/${tierId}`,
    /** Public ở backend - trang Return URL gọi được khi phụ huynh chưa đăng nhập. */
    paymentStatus: (gatewayTransactionId: string) =>
      `/api/billing/payments/${gatewayTransactionId}/status`,
    /** Cổng học tập: hoá đơn + lịch sử thanh toán của CHÍNH học viên (READ_LEARNING). */
    myInvoices: "/api/billing/me/invoices",
  },
  academic: {
    /** POST upsert điểm một cột - classId/enrollmentId/courseGradeColumnId nằm trong body. */
    grades: "/api/academic/grades",
    classGradebook: (classId: string) => `/api/academic/classes/${classId}/gradebook`,
    classGradebookExport: (classId: string) => `/api/academic/classes/${classId}/gradebook/export`,
    classProgress: (classId: string) => `/api/academic/classes/${classId}/progress`,
    classProgressExport: (classId: string) => `/api/academic/classes/${classId}/progress/export`,
    studentSummary: (studentProfileId: string) => `/api/academic/students/${studentProfileId}/summary`,
    /** Cổng học tập: lớp, lịch, điểm, tiến trình của CHÍNH học viên (READ_LEARNING). */
    myClasses: "/api/academic/me/classes",
  },
  attendance: {
    /** POST upsert một buổi theo (classId, sessionDate) - classId nằm trong body. */
    sessions: "/api/attendance/sessions",
    classAttendance: (classId: string) => `/api/attendance/classes/${classId}`,
    /** Cổng học tập: điểm danh của CHÍNH học viên (READ_LEARNING). */
    myClasses: "/api/attendance/me/classes",
  },
  assessment: {
    assignments: "/api/assessment/assignments",
    assignment: (assignmentId: string) => `/api/assessment/assignments/${assignmentId}`,
    assignmentResults: (assignmentId: string) => `/api/assessment/assignments/${assignmentId}/results`,
    classAssignments: (classId: string) => `/api/assessment/classes/${classId}/assignments`,
    miniTasks: "/api/assessment/mini-tasks",
    miniTask: (miniTaskId: string) => `/api/assessment/mini-tasks/${miniTaskId}`,
    classMiniTasks: (classId: string) => `/api/assessment/classes/${classId}/mini-tasks`,
    /** Cổng học tập: bài tập + mini-task của CHÍNH học viên (READ_LEARNING). */
    myClasses: "/api/assessment/me/classes",
    myMiniTask: (miniTaskId: string) => `/api/assessment/me/mini-tasks/${miniTaskId}`,
    myMiniTaskAttempts: (miniTaskId: string) => `/api/assessment/me/mini-tasks/${miniTaskId}/attempts`,
  },
} as const;

/**
 * Hai endpoint không bao giờ được thử refresh khi gặp 401: đăng nhập sai mật khẩu thì 401 là câu trả
 * lời cuối cùng, còn refresh thất bại mà lại gọi refresh nữa thì thành vòng lặp.
 */
export const ENDPOINTS_WITHOUT_SESSION_RETRY: readonly string[] = [
  API_ROUTE.auth.login,
  API_ROUTE.auth.completeInvite,
  API_ROUTE.auth.refresh,
  API_ROUTE.account.forgotPassword,
  API_ROUTE.account.resetPassword,
];

/**
 * Endpoint public có path động nên không so khớp được bằng danh sách chuỗi ở trên -
 * {@code apiClient} tự bỏ qua lượt refresh với mọi path bắt đầu bằng tiền tố này.
 */
export const PUBLIC_PATH_PREFIXES: readonly string[] = ["/api/billing/payments/"];
