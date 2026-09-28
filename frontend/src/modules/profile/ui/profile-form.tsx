import type { Session } from "@/entities/account";
import { UserAvatar } from "@/entities/account";
import { useUpdateProfile } from "@/modules/profile/api/use-profile-mutations";
import { profileFormSchema } from "@/modules/profile/model/profile-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { Check, Save } from "lucide-react";

export function ProfileForm({ session }: { readonly session: Session }) {
  const updateProfile = useUpdateProfile();
  const form = useZodForm({
    schema: profileFormSchema,
    initialValues: { fullName: session.fullName, avatarUrl: session.avatarUrl ?? "" },
    onSubmit: (values) => updateProfile.mutateAsync(values),
  });

  return (
    <GlassPanel>
      <form onSubmit={form.handleSubmit} className="flex flex-col gap-5" noValidate>
        <div className="flex items-center gap-4">
          <UserAvatar fullName={form.values.fullName} avatarUrl={form.values.avatarUrl} size="lg" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-mist-100">{session.email}</p>
            <p className="text-xs text-mist-500">
              {session.roleName}
              {session.branchName === null ? "" : ` · ${session.branchName}`}
            </p>
          </div>
        </div>

        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Họ tên" htmlFor="profile-full-name" error={form.fieldErrors.fullName}>
          <GlassInput
            id="profile-full-name"
            value={form.values.fullName}
            invalid={form.fieldErrors.fullName !== undefined}
            onChange={(event) => form.setValue("fullName", event.target.value)}
          />
        </FormField>

        <FormField
          label="Ảnh đại diện"
          htmlFor="profile-avatar-url"
          hint="Dán đường dẫn ảnh, hoặc để trống để dùng chữ cái đầu của tên."
          error={form.fieldErrors.avatarUrl}
        >
          <GlassInput
            id="profile-avatar-url"
            type="url"
            placeholder="https://..."
            value={form.values.avatarUrl}
            invalid={form.fieldErrors.avatarUrl !== undefined}
            onChange={(event) => form.setValue("avatarUrl", event.target.value)}
          />
        </FormField>

        <div className="flex items-center gap-3">
          <GlassButton type="submit" loading={form.isSubmitting} icon={<Save size={16} aria-hidden />}>
            Lưu thay đổi
          </GlassButton>
          {updateProfile.isSuccess && !form.isSubmitting ? (
            <span className="inline-flex items-center gap-1.5 text-sm text-aqua-300">
              <Check size={14} aria-hidden />
              Đã lưu
            </span>
          ) : null}
        </div>
      </form>
    </GlassPanel>
  );
}
