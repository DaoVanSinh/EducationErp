import { ResetPasswordForm } from "@/modules/auth/ui/reset-password-form";
import { APP_ROUTE, RESET_TOKEN_PARAM } from "@/shared/constants/app-routes";
import { EmptyState } from "@/shared/ui/empty-state";
import { GlassButton } from "@/shared/ui/glass-button";
import { Link2Off } from "lucide-react";
import { Link, useSearchParams } from "react-router-dom";

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get(RESET_TOKEN_PARAM) ?? "";

  if (token.length === 0) {
    return (
      <EmptyState
        icon={<Link2Off size={28} aria-hidden />}
        title="Liên kết không hợp lệ"
        description="Liên kết đặt lại mật khẩu thiếu mã xác thực. Hãy yêu cầu gửi lại từ trang quên mật khẩu."
        action={
          <Link to={APP_ROUTE.forgotPassword}>
            <GlassButton size="sm">Yêu cầu liên kết mới</GlassButton>
          </Link>
        }
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Đặt mật khẩu mới</h1>
        <p className="mt-1 text-sm text-mist-400">Mật khẩu mới sẽ có hiệu lực ngay sau khi lưu.</p>
      </div>
      <ResetPasswordForm token={token} />
    </div>
  );
}
