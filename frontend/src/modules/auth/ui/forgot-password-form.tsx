import { useForgotPasswordController } from "@/modules/auth/hooks/use-forgot-password-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { MailCheck, Send } from "lucide-react";

export function ForgotPasswordForm() {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    isSuccess,
    handleSubmit,
    setEmail,
  } = useForgotPasswordController();

  if (isSuccess) {
    return (
      <div className="flex flex-col items-center gap-3 text-center">
        <MailCheck size={32} className="text-orange-500" aria-hidden />
        <p className="text-sm font-medium text-slate-800">
          Nếu email vừa nhập có tài khoản, liên kết đặt lại mật khẩu đã được gửi tới hộp thư đó.
        </p>
        <p className="text-xs text-slate-500">Liên kết có hiệu lực trong 30 phút.</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <FormField
        label="Email"
        htmlFor="forgot-email"
        hint="Nhập email đã đăng ký để nhận liên kết đặt lại mật khẩu."
        error={fieldErrors.email}
      >
        <GlassInput
          id="forgot-email"
          type="email"
          autoComplete="username"
          autoFocus
          value={values.email}
          invalid={fieldErrors.email !== undefined}
          onChange={(event) => setEmail(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<Send size={16} aria-hidden />}>
        Gửi liên kết
      </GlassButton>
    </form>
  );
}
