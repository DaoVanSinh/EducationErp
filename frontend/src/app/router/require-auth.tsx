import { DashboardLayout } from "@/app/layouts/dashboard-layout";
import { FullScreenLoader } from "@/app/ui/full-screen-loader";
import { accountKeys, useSession } from "@/entities/account";
import { SelectedBranchProvider } from "@/entities/branch";
import { useNotificationStream } from "@/entities/notification";
import { PermissionProvider } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { Navigate, useLocation } from "react-router-dom";

/**
 * Cửa vào các trang cần đăng nhập. Chưa có phiên thì về trang đăng nhập kèm đường dẫn đang muốn vào,
 * để sau khi đăng nhập quay lại đúng chỗ đó.
 *
 * Lỗi mạng được phân biệt với "chưa đăng nhập": đá người dùng ra trang đăng nhập khi server chỉ đang
 * khởi động lại sẽ làm họ tưởng mình bị đăng xuất.
 */
export function RequireAuth() {
  const session = useSession();
  const location = useLocation();

  if (session.isPending) {
    return <FullScreenLoader label="Đang kiểm tra phiên làm việc" />;
  }

  if (session.isError) {
    return (
      <div className="flex min-h-dvh items-center justify-center p-4">
        <GlassPanel className="w-full max-w-md">
          <ErrorNotice
            error={session.error}
            action={
              <GlassButton size="sm" onClick={() => void session.refetch()}>
                Thử lại
              </GlassButton>
            }
          />
        </GlassPanel>
      </div>
    );
  }

  if (session.data === null || session.data === undefined) {
    return <Navigate to={APP_ROUTE.login} state={{ from: location.pathname }} replace />;
  }

  return <AuthenticatedShell session={session.data} />;
}

function AuthenticatedShell({ session }: { readonly session: NonNullable<ReturnType<typeof useSession>["data"]> }) {
  const queryClient = useQueryClient();
  // onPermissionChanged phải ổn định tham chiếu qua các lần render (useCallback) - useNotificationStream
  // đặt nó trong dependency array của effect, một arrow function mới mỗi lần render sẽ khiến EventSource
  // bị đóng và mở lại ở mọi lần điều hướng/render thay vì giữ một kết nối SSE duy nhất suốt phiên.
  const onPermissionChanged = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: accountKeys.session() });
  }, [queryClient]);
  // Hook phải gọi vô điều kiện (rule of hooks) - tách component riêng để chỉ mở EventSource khi đã
  // chắc chắn có phiên, không vi phạm thứ tự hook trong RequireAuth (ba nhánh return sớm ở trên).
  useNotificationStream(onPermissionChanged);
  return (
    <PermissionProvider permissions={session.permissions}>
      <SelectedBranchProvider>
        <DashboardLayout session={session} />
      </SelectedBranchProvider>
    </PermissionProvider>
  );
}
