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
  enrollments: "/admin/enrollments",
  invoices: "/admin/billing/invoices",
  invoiceDetail: "/admin/billing/invoices/:invoiceId",
  combos: "/admin/billing/combos",
  comboDetail: "/admin/billing/combos/:comboId",
  comboDiscountTiers: "/admin/billing/combo-discount-tiers",
  /** Public: phụ huynh quay về từ cổng thanh toán, có thể chưa đăng nhập (spec mục 10). */
  paymentReturn: "/payment/return/:gateway",
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

/** Link thật tới trang chi tiết một hoá đơn - APP_ROUTE.invoiceDetail chỉ là route template. */
export function buildInvoiceDetailPath(invoiceId: string): string {
  return `/admin/billing/invoices/${invoiceId}`;
}

/** Link thật tới trang Return URL của một cổng - phải khớp payment.*.redirect-url/return-url ở backend. */
export function buildPaymentReturnPath(gateway: string): string {
  return `/payment/return/${gateway}`;
}

/** Link thật tới trang chi tiết một combo - APP_ROUTE.comboDetail chỉ là route template. */
export function buildComboDetailPath(comboId: string): string {
  return `/admin/billing/combos/${comboId}`;
}

/** Tên param cổng thanh toán trong APP_ROUTE.paymentReturn, dùng với useParams(). */
export const PAYMENT_RETURN_GATEWAY_PARAM = "gateway";
