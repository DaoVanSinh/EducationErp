import { useResetPasswordController } from "@/modules/auth/hooks/use-reset-password-controller";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { PasswordInput } from "@/shared/ui/password-input";
import { KeyRound } from "lucide-react";
import { Link } from "react-router-dom";

export function ResetPasswordForm({ token }: { readonly token: string }) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    isSuccess,
    handleSubmit,
    setNewPassword,
    setConfirmPassword,
  } = useResetPasswordController(token);

  if (isSuccess) {
    return (
      <div className="flex flex-col gap-4 text-center">
        <p className="text-sm font-medium text-slate-800">
          Đã đổi mật khẩu. Mọi phiên đang đăng nhập của tài khoản này đã bị đóng.
        </p>
        <Link to={APP_ROUTE.login}>
          <GlassButton className="w-full">Đăng nhập lại</GlassButton>
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <FormField
        label="Mật khẩu mới"
        htmlFor="reset-password"
        hint="Tối thiểu 8 ký tự."
        error={fieldErrors.newPassword}
      >
        <PasswordInput
          id="reset-password"
          autoComplete="new-password"
          autoFocus
          value={values.newPassword}
          invalid={fieldErrors.newPassword !== undefined}
          onChange={(event) => setNewPassword(event.target.value)}
        />
      </FormField>

      <FormField label="Nhập lại mật khẩu" htmlFor="reset-confirm" error={fieldErrors.confirmPassword}>
        <PasswordInput
          id="reset-confirm"
          autoComplete="new-password"
          value={values.confirmPassword}
          invalid={fieldErrors.confirmPassword !== undefined}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<KeyRound size={16} aria-hidden />}>
        Đặt mật khẩu mới
      </GlassButton>
    </form>
  );
}
