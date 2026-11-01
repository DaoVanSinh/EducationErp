import { AuthLayout } from "@/app/layouts/auth-layout";
import { RedirectWhenSignedIn } from "@/app/router/redirect-when-signed-in";
import { RequireAuth } from "@/app/router/require-auth";
import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { APP_ROUTE, LANDING_LEGACY_PATH } from "@/shared/constants/app-routes";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { FileQuestion } from "lucide-react";
import { lazy, Suspense } from "react";
import { Link, Navigate, Route, Routes } from "react-router-dom";

/**
 * Mỗi trang là một chunk riêng: người chỉ vào xem hồ sơ của mình không phải tải kèm cả màn hình quản
 * trị RBAC mà họ không có quyền mở.
 */
const LoginPage = lazy(() => import("@/modules/auth").then((module) => ({ default: module.LoginPage })));
const ForgotPasswordPage = lazy(() =>
  import("@/modules/auth").then((module) => ({ default: module.ForgotPasswordPage })),
);
const ResetPasswordPage = lazy(() =>
  import("@/modules/auth").then((module) => ({ default: module.ResetPasswordPage })),
);
const DashboardPage = lazy(() =>
  import("@/modules/dashboard").then((module) => ({ default: module.DashboardPage })),
);
const ProfilePage = lazy(() =>
  import("@/modules/profile").then((module) => ({ default: module.ProfilePage })),
);
const McpConnectorPage = lazy(() =>
  import("@/modules/mcp-connector").then((module) => ({ default: module.McpConnectorPage })),
);
const AccountsPage = lazy(() => import("@/modules/rbac").then((module) => ({ default: module.AccountsPage })));
const RolesPage = lazy(() => import("@/modules/rbac").then((module) => ({ default: module.RolesPage })));
const BranchesPage = lazy(() =>
  import("@/modules/organization").then((module) => ({ default: module.BranchesPage })),
);
const CoursesPage = lazy(() =>
  import("@/modules/courses").then((module) => ({ default: module.CoursesPage })),
);
const ClassesPage = lazy(() =>
  import("@/modules/courses").then((module) => ({ default: module.ClassesPage })),
);
const TeachersPage = lazy(() =>
  import("@/modules/teachers").then((module) => ({ default: module.TeachersPage })),
);
const TeacherDetailPage = lazy(() =>
  import("@/modules/teachers").then((module) => ({ default: module.TeacherDetailPage })),
);
const StudentsPage = lazy(() =>
  import("@/modules/students").then((module) => ({ default: module.StudentsPage })),
);
const StudentDetailPage = lazy(() =>
  import("@/modules/students").then((module) => ({ default: module.StudentDetailPage })),
);
const ContractsPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.ContractsPage })),
);
const PayrollRunsPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.PayrollRunsPage })),
);
const PayrollRunDetailPage = lazy(() =>
  import("@/modules/payroll").then((module) => ({ default: module.PayrollRunDetailPage })),
);
const EnrollmentsPage = lazy(() =>
  import("@/modules/enrollment").then((module) => ({ default: module.EnrollmentsPage })),
);
const InvoicesPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.InvoicesPage })),
);
const InvoiceDetailPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.InvoiceDetailPage })),
);
const CombosPage = lazy(() => import("@/modules/billing").then((module) => ({ default: module.CombosPage })));
const ComboDetailPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.ComboDetailPage })),
);
const ComboDiscountTiersPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.ComboDiscountTiersPage })),
);
const GradebookPage = lazy(() =>
  import("@/modules/academic").then((module) => ({ default: module.GradebookPage })),
);
const ClassProgressPage = lazy(() =>
  import("@/modules/academic").then((module) => ({ default: module.ClassProgressPage })),
);
const PaymentReturnPage = lazy(() =>
  import("@/modules/billing").then((module) => ({ default: module.PaymentReturnPage })),
);
const LandingPage = lazy(() =>
  import("@/modules/landing").then((module) => ({ default: module.LandingPage })),
);
const CenterConfigPage = lazy(() =>
  import("@/modules/landing").then((module) => ({ default: module.CenterConfigPage })),
);

function NotFoundPage() {
  return (
    <div className="flex min-h-dvh items-center justify-center p-4">
      <GlassPanel className="w-full max-w-md">
        <EmptyState
          icon={<FileQuestion size={28} aria-hidden />}
          title="Không tìm thấy trang"
          description="Đường dẫn này không tồn tại hoặc đã được đổi."
          action={
            <Link to={APP_ROUTE.dashboard}>
              <GlassButton size="sm">Về trang tổng quan</GlassButton>
            </Link>
          }
        />
      </GlassPanel>
    </div>
  );
}

export function AppRouter() {
  return (
    <Suspense fallback={<FullScreenLoader />}>
      <Routes>
        <Route path={APP_ROUTE.landing} element={<LandingPage />} />
        <Route path={LANDING_LEGACY_PATH} element={<Navigate to={APP_ROUTE.landing} replace />} />

        <Route element={<AuthLayout />}>
          <Route element={<RedirectWhenSignedIn />}>
            <Route path={APP_ROUTE.login} element={<LoginPage />} />
            <Route path={APP_ROUTE.forgotPassword} element={<ForgotPasswordPage />} />
          </Route>
          <Route path={APP_ROUTE.resetPassword} element={<ResetPasswordPage />} />
          <Route path={APP_ROUTE.paymentReturn} element={<PaymentReturnPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route path={APP_ROUTE.dashboard} element={<DashboardPage />} />
          <Route path={APP_ROUTE.accounts} element={<AccountsPage />} />
          <Route path={APP_ROUTE.branches} element={<BranchesPage />} />
          <Route path={APP_ROUTE.centerConfig} element={<CenterConfigPage />} />
          <Route path={APP_ROUTE.courses} element={<CoursesPage />} />
          <Route path={APP_ROUTE.classes} element={<ClassesPage />} />
          <Route path={APP_ROUTE.teachers} element={<TeachersPage />} />
          <Route path={APP_ROUTE.teacherDetail} element={<TeacherDetailPage />} />
          <Route path={APP_ROUTE.students} element={<StudentsPage />} />
          <Route path={APP_ROUTE.studentDetail} element={<StudentDetailPage />} />
          <Route path={APP_ROUTE.payrollContracts} element={<ContractsPage />} />
          <Route path={APP_ROUTE.payrollRuns} element={<PayrollRunsPage />} />
          <Route path={APP_ROUTE.payrollRunDetail} element={<PayrollRunDetailPage />} />
          <Route path={APP_ROUTE.enrollments} element={<EnrollmentsPage />} />
          <Route path={APP_ROUTE.invoices} element={<InvoicesPage />} />
          <Route path={APP_ROUTE.invoiceDetail} element={<InvoiceDetailPage />} />
          <Route path={APP_ROUTE.combos} element={<CombosPage />} />
          <Route path={APP_ROUTE.comboDetail} element={<ComboDetailPage />} />
          <Route path={APP_ROUTE.comboDiscountTiers} element={<ComboDiscountTiersPage />} />
          <Route path={APP_ROUTE.classGradebook} element={<GradebookPage />} />
          <Route path={APP_ROUTE.classProgress} element={<ClassProgressPage />} />
          <Route path={APP_ROUTE.roles} element={<RolesPage />} />
          <Route path={APP_ROUTE.profile} element={<ProfilePage />} />
          <Route path={APP_ROUTE.mcpConnector} element={<McpConnectorPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
