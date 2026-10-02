import { AuthLayout } from "@/app/layouts/auth-layout";
import { RedirectWhenSignedIn } from "@/app/router/redirect-when-signed-in";
import { RequireAuth } from "@/app/router/require-auth";
import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { FileQuestion } from "lucide-react";
import { lazy, Suspense } from "react";
import { Link, Route, Routes } from "react-router-dom";

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
        <Route element={<AuthLayout />}>
          <Route element={<RedirectWhenSignedIn />}>
            <Route path={APP_ROUTE.login} element={<LoginPage />} />
            <Route path={APP_ROUTE.forgotPassword} element={<ForgotPasswordPage />} />
          </Route>
          <Route path={APP_ROUTE.resetPassword} element={<ResetPasswordPage />} />
        </Route>

        <Route element={<RequireAuth />}>
          <Route path={APP_ROUTE.dashboard} element={<DashboardPage />} />
          <Route path={APP_ROUTE.accounts} element={<AccountsPage />} />
          <Route path={APP_ROUTE.branches} element={<BranchesPage />} />
          <Route path={APP_ROUTE.courses} element={<CoursesPage />} />
          <Route path={APP_ROUTE.classes} element={<ClassesPage />} />
          <Route path={APP_ROUTE.roles} element={<RolesPage />} />
          <Route path={APP_ROUTE.profile} element={<ProfilePage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </Suspense>
  );
}
