import { useLoginFormController } from "@/modules/auth/hooks/use-login-form-controller";
import { CompleteInviteForm } from "@/modules/auth/ui/complete-invite-form";
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
  const redirectTo = state?.from ?? APP_ROUTE.dashboard;

  // Controller phải nằm ở đây (không phải trong LoginForm) vì cả hai nhánh (form đăng nhập / form đổi
  // mật khẩu lần đầu) đều cần đọc requiresPasswordChange từ cùng một state - tách ra sẽ phải nâng state
  // lên một cấp nữa, không khác gì đặt ở đây từ đầu.
  const loginController = useLoginFormController(redirectTo);

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">
          {loginController.requiresPasswordChange ? "Đặt mật khẩu mới" : "Đăng nhập"}
        </h1>
        <p className="mt-1 text-sm text-slate-500">Hệ thống quản trị người dùng EduERP.</p>
      </div>
      {loginController.requiresPasswordChange ? (
        <CompleteInviteForm
          email={loginController.email}
          temporaryPassword={loginController.temporaryPassword}
          redirectTo={redirectTo}
        />
      ) : (
        <LoginForm controller={loginController} />
      )}
    </div>
  );
}
