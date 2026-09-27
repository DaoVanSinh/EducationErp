import { useResetPassword } from "@/modules/auth/api/use-auth-mutations";
import { resetPasswordFormSchema } from "@/modules/auth/model/auth-forms";
import { PasswordInput } from "@/shared/ui/password-input";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { KeyRound } from "lucide-react";
import { Link } from "react-router-dom";

export function ResetPasswordForm({ token }: { readonly token: string }) {
  const resetPassword = useResetPassword();
  const form = useZodForm({
    schema: resetPasswordFormSchema,
    initialValues: { token, newPassword: "", confirmPassword: "" },
    onSubmit: (values) =>
      resetPassword.mutateAsync({ token: values.token, newPassword: values.newPassword }),
  });

  if (resetPassword.isSuccess) {
    return (
      <div className="flex flex-col gap-4 text-center">
        <p className="text-sm text-mist-200">
          Đã đổi mật khẩu. Mọi phiên đang đăng nhập của tài khoản này đã bị đóng.
        </p>
        <Link to={APP_ROUTE.login}>
          <GlassButton className="w-full">Đăng nhập lại</GlassButton>
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={form.handleSubmit} className="flex flex-col gap-5" noValidate>
      {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

      <FormField
        label="Mật khẩu mới"
        htmlFor="reset-password"
        hint="Tối thiểu 8 ký tự."
        error={form.fieldErrors.newPassword}
      >
        <PasswordInput
          id="reset-password"
          autoComplete="new-password"
          autoFocus
          value={form.values.newPassword}
          invalid={form.fieldErrors.newPassword !== undefined}
          onChange={(event) => form.setValue("newPassword", event.target.value)}
        />
      </FormField>

      <FormField label="Nhập lại mật khẩu" htmlFor="reset-confirm" error={form.fieldErrors.confirmPassword}>
        <PasswordInput
          id="reset-confirm"
          autoComplete="new-password"
          value={form.values.confirmPassword}
          invalid={form.fieldErrors.confirmPassword !== undefined}
          onChange={(event) => form.setValue("confirmPassword", event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={form.isSubmitting} icon={<KeyRound size={16} aria-hidden />}>
        Đặt mật khẩu mới
      </GlassButton>
    </form>
  );
}
