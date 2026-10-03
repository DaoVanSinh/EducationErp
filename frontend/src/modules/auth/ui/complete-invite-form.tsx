import { useCompleteInviteController } from "@/modules/auth/hooks/use-complete-invite-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { PasswordInput } from "@/shared/ui/password-input";
import { KeyRound } from "lucide-react";

export interface CompleteInviteFormProps {
  readonly email: string;
  readonly temporaryPassword: string;
  readonly redirectTo: string;
}

export function CompleteInviteForm({ email, temporaryPassword, redirectTo }: CompleteInviteFormProps) {
  const { values, fieldErrors, submitError, isSubmitting, handleSubmit, setNewPassword, setConfirmPassword } =
    useCompleteInviteController({ email, temporaryPassword, redirectTo });

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
      {submitError ? <ErrorNotice error={submitError} /> : null}

      <p className="text-sm text-slate-500">Đây là lần đăng nhập đầu tiên — hãy đặt mật khẩu mới trước khi tiếp tục.</p>

      <FormField label="Mật khẩu mới" htmlFor="complete-invite-new-password" error={fieldErrors.newPassword}>
        <PasswordInput
          id="complete-invite-new-password"
          autoComplete="new-password"
          autoFocus
          value={values.newPassword}
          invalid={fieldErrors.newPassword !== undefined}
          onChange={(event) => setNewPassword(event.target.value)}
        />
      </FormField>

      <FormField label="Xác nhận mật khẩu mới" htmlFor="complete-invite-confirm-password" error={fieldErrors.confirmPassword}>
        <PasswordInput
          id="complete-invite-confirm-password"
          autoComplete="new-password"
          value={values.confirmPassword}
          invalid={fieldErrors.confirmPassword !== undefined}
          onChange={(event) => setConfirmPassword(event.target.value)}
        />
      </FormField>

      <GlassButton type="submit" loading={isSubmitting} icon={<KeyRound size={16} aria-hidden />}>
        Đặt mật khẩu và đăng nhập
      </GlassButton>
    </form>
  );
}
