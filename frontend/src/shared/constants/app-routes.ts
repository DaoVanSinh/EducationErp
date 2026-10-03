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
  payrollContracts: "/admin/payroll/contracts",
  payrollRuns: "/admin/payroll/runs",
  payrollRunDetail: "/admin/payroll/runs/:runId",
  login: "/login",
  forgotPassword: "/forgot-password",
  resetPassword: "/reset-password",
} as const;

/** Link thật tới trang chi tiết một kỳ lương - APP_ROUTE.payrollRunDetail chỉ là route template cho <Route path>. */
export function buildPayrollRunDetailPath(runId: string): string {
  return `/admin/payroll/runs/${runId}`;
}

/** Tham số query mang token trong link email đặt lại mật khẩu. */
export const RESET_TOKEN_PARAM = "token";
