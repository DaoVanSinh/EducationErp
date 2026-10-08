import { ForgotPasswordForm } from "@/modules/auth/ui/forgot-password-form";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ArrowLeft } from "lucide-react";
import { Link } from "react-router-dom";

export function ForgotPasswordPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-slate-900">Quên mật khẩu</h1>
        <p className="mt-1 text-sm text-slate-500">Chúng tôi sẽ gửi liên kết đặt lại qua email.</p>
      </div>
      <ForgotPasswordForm />
      <Link
        to={APP_ROUTE.login}
        className="inline-flex items-center justify-center gap-2 text-sm font-medium text-slate-500 transition-colors hover:text-orange-600"
      >
        <ArrowLeft size={14} aria-hidden />
        Về trang đăng nhập
      </Link>
    </div>
  );
}
