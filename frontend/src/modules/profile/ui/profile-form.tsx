import type { Session } from "@/entities/account";
import { UserAvatar } from "@/entities/account";
import { useProfileFormController } from "@/modules/profile/hooks/use-profile-form-controller";
import { PROFILE_AVATAR } from "@/modules/profile/model/profile-forms";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Check, ImageUp, RotateCcw, Save } from "lucide-react";
import { useId, useRef } from "react";

export function ProfileForm({ session }: { readonly session: Session }) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    isSuccess,
    displayAvatarUrl,
    avatarFileName,
    avatarError,
    hasAvatar,
    handleSubmit,
    setFullName,
    pickAvatar,
    removeAvatar,
  } = useProfileFormController(session);
  const fileInputId = useId();
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  return (
    <GlassPanel>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5" noValidate>
        <div className="flex items-center gap-4">
          <UserAvatar fullName={values.fullName} avatarUrl={displayAvatarUrl} size="lg" />
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
          label={PROFILE_AVATAR.LABEL}
          htmlFor={fileInputId}
          hint={PROFILE_AVATAR.HINT}
          error={avatarError}
        >
          <div className="flex flex-wrap items-center gap-3">
            <input
              ref={fileInputRef}
              id={fileInputId}
              type="file"
              accept={PROFILE_AVATAR.ACCEPT}
              disabled={isSubmitting}
              className="sr-only"
              onChange={(event) => {
                pickAvatar(event.target.files?.[0]);
                event.target.value = "";
              }}
            />
            <GlassButton
              type="button"
              variant="secondary"
              disabled={isSubmitting}
              icon={<ImageUp size={16} aria-hidden />}
              onClick={() => fileInputRef.current?.click()}
            >
              {hasAvatar ? PROFILE_AVATAR.CHANGE : PROFILE_AVATAR.PICK}
            </GlassButton>
            {hasAvatar ? (
              <GlassButton
                type="button"
                variant="ghost"
                disabled={isSubmitting}
                icon={<RotateCcw size={15} aria-hidden />}
                onClick={removeAvatar}
              >
                {PROFILE_AVATAR.REMOVE}
              </GlassButton>
            ) : null}
            {avatarFileName !== null ? (
              <span className="truncate text-xs font-medium text-slate-600">
                Đã chọn: {avatarFileName}
              </span>
            ) : null}
          </div>
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
