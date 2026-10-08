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
    permissionGroups: "/api/rbac/permission-groups",
    accountGroups: (accountId: string) => `/api/rbac/accounts/${accountId}/groups`,
    accountBranch: (accountId: string) => `/api/rbac/accounts/${accountId}/transfer-branch`,
    accountResendInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/resend-invite`,
    accountRevokeInvite: (accountId: string) => `/api/rbac/accounts/${accountId}/revoke-invite`,
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
