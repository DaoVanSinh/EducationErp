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
  teacherDetail: "/admin/teachers/:profileId",
  students: "/admin/students",
  studentDetail: "/admin/students/:profileId",
  payrollContracts: "/admin/payroll/contracts",
  payrollRuns: "/admin/payroll/runs",
  payrollRunDetail: "/admin/payroll/runs/:runId",
  enrollments: "/admin/enrollments",
  invoices: "/admin/billing/invoices",
  invoiceDetail: "/admin/billing/invoices/:invoiceId",
  combos: "/admin/billing/combos",
  comboDetail: "/admin/billing/combos/:comboId",
  comboDiscountTiers: "/admin/billing/combo-discount-tiers",
  classGradebook: "/admin/academic/classes/:classId/gradebook",
  classProgress: "/admin/academic/classes/:classId/progress",
  /** Cổng giảng dạy - lớp giáo viên đang đăng nhập được phân công dạy. */
  myTeachingClasses: "/teaching/classes",
  /** Cổng học tập - lớp, lịch, điểm, tiến trình của học viên đang đăng nhập. */
  myLearning: "/learning/classes",
  /** Cổng học tập - học phí và lịch sử thanh toán của học viên đang đăng nhập. */
  myInvoices: "/learning/invoices",
  /** Cổng học tập - điểm danh của học viên đang đăng nhập. */
  myAttendance: "/learning/attendance",
  /** Cổng học tập - bài tập/mini-test và mini-task của học viên đang đăng nhập. */
  myAssessments: "/learning/assessments",
  /** Trang làm một mini-task (route template). */
  practiceMiniTask: "/learning/mini-tasks/:miniTaskId",
  classAttendance: "/admin/attendance/classes/:classId",
  classAssignments: "/admin/assessment/classes/:classId/assignments",
  classMiniTasks: "/admin/assessment/classes/:classId/mini-tasks",
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

/** Link thật tới sổ điểm của một lớp - APP_ROUTE.classGradebook chỉ là route template. */
export function buildClassGradebookPath(classId: string): string {
  return `/admin/academic/classes/${classId}/gradebook`;
}

/** Link thật tới bảng tiến trình học tập của một lớp. */
export function buildClassProgressPath(classId: string): string {
  return `/admin/academic/classes/${classId}/progress`;
}

/** Link thật tới trang chi tiết một giáo viên - APP_ROUTE.teacherDetail chỉ là route template. */
export function buildTeacherDetailPath(profileId: string): string {
  return `/admin/teachers/${profileId}`;
}

/** Link thật tới trang chi tiết một học viên - APP_ROUTE.studentDetail chỉ là route template. */
export function buildStudentDetailPath(profileId: string): string {
  return `/admin/students/${profileId}`;
}

/** Link thật tới bảng điểm danh của một lớp. */
export function buildClassAttendancePath(classId: string): string {
  return `/admin/attendance/classes/${classId}`;
}

/** Link thật tới trang bài tập / mini-test của một lớp. */
export function buildClassAssignmentsPath(classId: string): string {
  return `/admin/assessment/classes/${classId}/assignments`;
}

/** Link thật tới trang mini-task của một lớp. */
export function buildClassMiniTasksPath(classId: string): string {
  return `/admin/assessment/classes/${classId}/mini-tasks`;
}

/** Link thật tới trang làm một mini-task của học viên. */
export function buildPracticeMiniTaskPath(miniTaskId: string): string {
  return `/learning/mini-tasks/${miniTaskId}`;
}
