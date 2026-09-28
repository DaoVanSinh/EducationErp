import { ForgotPasswordForm } from "@/modules/auth/ui/forgot-password-form";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export function ForgotPasswordPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Quên mật khẩu</h1>
        <p className="mt-1 text-sm text-mist-400">Chúng tôi sẽ gửi liên kết đặt lại qua email.</p>
      </div>
      <ForgotPasswordForm />
      <Link
        to={APP_ROUTE.login}
        className="inline-flex items-center justify-center gap-2 text-sm text-mist-400 transition-colors hover:text-aqua-300"
      >
        <ArrowLeft size={14} aria-hidden />
        Về trang đăng nhập
      </Link>
    </div>
  );
}
