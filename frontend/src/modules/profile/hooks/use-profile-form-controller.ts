import type { Session } from "@/entities/account";
import { useUpdateProfile } from "@/modules/profile/api/use-profile-mutations";
import { profileFormSchema } from "@/modules/profile/model/profile-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useProfileFormController(session: Session) {
  const updateProfile = useUpdateProfile();
  const form = useZodForm({
    schema: profileFormSchema,
    initialValues: { fullName: session.fullName, avatarUrl: session.avatarUrl ?? "" },
    onSubmit: (values) => updateProfile.mutateAsync(values),
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    isSuccess: updateProfile.isSuccess && !form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setFullName: (fullName: string) => form.setValue("fullName", fullName),
    setAvatarUrl: (avatarUrl: string) => form.setValue("avatarUrl", avatarUrl),
  };
}
