import { LoginForm } from "@/modules/auth/ui/login-form";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { useLocation } from "react-router-dom";

interface RedirectState {
  readonly from?: string;
}

/**
 * Sau khi đăng nhập, quay lại đúng trang người dùng định vào trước khi bị chặn - state.from do route
 * bảo vệ gắn vào lúc điều hướng sang đây.
 */
export function LoginPage() {
  const location = useLocation();
  const state = location.state as RedirectState | null;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Đăng nhập</h1>
        <p className="mt-1 text-sm text-mist-400">Hệ thống quản trị người dùng EduERP.</p>
      </div>
      <LoginForm redirectTo={state?.from ?? APP_ROUTE.dashboard} />
    </div>
  );
}
