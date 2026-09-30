import type { Session } from "@/entities/account";
import { UserAvatar } from "@/entities/account";
import { useProfileFormController } from "@/modules/profile/hooks/use-profile-form-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Check, Save } from "lucide-react";

export function ProfileForm({ session }: { readonly session: Session }) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    isSuccess,
    handleSubmit,
    setFullName,
    setAvatarUrl,
  } = useProfileFormController(session);

  return (
    <GlassPanel>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
        <div className="flex items-center gap-4">
          <UserAvatar fullName={values.fullName} avatarUrl={values.avatarUrl} size="lg" />
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-slate-900">{session.email}</p>
            <p className="text-xs text-slate-500">
              {session.roleName}
              {session.branchName === null ? "" : ` · ${session.branchName}`}
            </p>
          </div>
        </div>

        {submitError ? <ErrorNotice error={submitError} /> : null}

        <FormField label="Họ tên" htmlFor="profile-full-name" error={fieldErrors.fullName}>
          <GlassInput
            id="profile-full-name"
            value={values.fullName}
            invalid={fieldErrors.fullName !== undefined}
            onChange={(event) => setFullName(event.target.value)}
          />
        </FormField>

        <FormField
          label="Ảnh đại diện"
          htmlFor="profile-avatar-url"
          hint="Dán đường dẫn ảnh, hoặc để trống để dùng chữ cái đầu của tên."
          error={fieldErrors.avatarUrl}
        >
          <GlassInput
            id="profile-avatar-url"
            type="url"
            placeholder="https://..."
            value={values.avatarUrl}
            invalid={fieldErrors.avatarUrl !== undefined}
            onChange={(event) => setAvatarUrl(event.target.value)}
          />
        </FormField>

        <div className="flex items-center gap-3">
          <GlassButton type="submit" loading={isSubmitting} icon={<Save size={16} aria-hidden />}>
            Lưu thay đổi
          </GlassButton>
          {isSuccess ? (
            <span className="inline-flex items-center gap-1.5 text-sm font-medium text-emerald-600">
              <Check size={14} aria-hidden />
              Đã lưu
            </span>
          ) : null}
        </div>
      </form>
    </GlassPanel>
  );
}
