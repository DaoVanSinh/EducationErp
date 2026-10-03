/**
 * Đường dẫn của SPA. {@link APP_ROUTE.resetPassword} phải khớp identity.frontend-reset-url ở backend,
 * vì đó là link được gửi trong email đặt lại mật khẩu.
 */
export const APP_ROUTE = {
  dashboard: "/",
  profile: "/profile",
  accounts: "/admin/accounts",
  roles: "/admin/roles",
  branches: "/admin/branches",
  courses: "/admin/courses",
  classes: "/admin/classes",
  teachers: "/admin/teachers",
  students: "/admin/students",
  login: "/login",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
} as const;

/** Tham số query mang token trong link email đặt lại mật khẩu. */
export const RESET_TOKEN_PARAM = "token";
