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
