/** Đường dẫn API. Một chỗ duy nhất, để đổi endpoint không phải đi tìm chuỗi trong component. */
export const API_ROUTE = {
  auth: {
    login: "/api/auth/login",
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
  },
  dashboard: {
    stats: "/api/dashboard/stats",
  },
} as const;

/**
 * Hai endpoint không bao giờ được thử refresh khi gặp 401: đăng nhập sai mật khẩu thì 401 là câu trả
 * lời cuối cùng, còn refresh thất bại mà lại gọi refresh nữa thì thành vòng lặp.
 */
export const ENDPOINTS_WITHOUT_SESSION_RETRY: readonly string[] = [
  API_ROUTE.auth.login,
  API_ROUTE.auth.refresh,
  API_ROUTE.account.forgotPassword,
  API_ROUTE.account.resetPassword,
];
