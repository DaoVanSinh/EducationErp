import type { Session } from "@/entities/account";
import { useSaveAvatar, useUpdateProfile } from "@/modules/profile/api/use-profile-mutations";
import { PROFILE_AVATAR, profileFormSchema } from "@/modules/profile/model/profile-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useEffect, useRef, useState } from "react";

function isValidAvatarFile(file: File): boolean {
  return (
    file.size > 0 &&
    file.size <= PROFILE_AVATAR.MAX_BYTES &&
    PROFILE_AVATAR.TYPES.includes(file.type)
  );
}

function resolveDisplayAvatarUrl(
  previewUrl: string,
  avatarRemoved: boolean,
  sessionAvatarUrl: string | null,
): string | null {
  if (previewUrl.length > 0) {
    return previewUrl;
  }
  if (avatarRemoved) {
    return null;
  }
  return sessionAvatarUrl;
}

export function useProfileFormController(session: Session) {
  const updateProfile = useUpdateProfile();
  const saveAvatar = useSaveAvatar();
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState("");
  const [avatarRemoved, setAvatarRemoved] = useState(false);
  const [avatarError, setAvatarError] = useState<string | undefined>(undefined);
  const generation = useRef(0);

  useEffect(
    () => () => {
      if (avatarPreview.length > 0) {
        URL.revokeObjectURL(avatarPreview);
      }
    },
    [avatarPreview],
  );

  const pickAvatar = (file: File | undefined) => {
    const current = ++generation.current;
    setAvatarFile(null);
    setAvatarPreview("");
    setAvatarError(undefined);
    if (!file) {
      return;
    }
    if (!isValidAvatarFile(file)) {
      if (current === generation.current) {
        setAvatarError(PROFILE_AVATAR.INVALID);
      }
      return;
    }
    if (current === generation.current) {
      setAvatarRemoved(false);
      setAvatarFile(file);
      setAvatarPreview(URL.createObjectURL(file));
    }
  };

  const removeAvatar = () => {
    generation.current++;
    setAvatarFile(null);
    setAvatarPreview("");
    setAvatarError(undefined);
    setAvatarRemoved(true);
  };

  const form = useZodForm({
    schema: profileFormSchema,
    initialValues: { fullName: session.fullName },
    onSubmit: async (values) => {
      let nextAvatarUrl = session.avatarUrl;
      if (avatarFile !== null) {
        const uploaded = await saveAvatar.mutateAsync(avatarFile);
        nextAvatarUrl = uploaded.avatarUrl;
      } else if (avatarRemoved && session.avatarUrl !== null) {
        const cleared = await saveAvatar.mutateAsync(null);
        nextAvatarUrl = cleared.avatarUrl;
      }
      await updateProfile.mutateAsync({
        fullName: values.fullName,
        avatarUrl: nextAvatarUrl,
      });
      setAvatarFile(null);
      setAvatarPreview("");
      setAvatarRemoved(false);
    },
  });

  const displayAvatarUrl = resolveDisplayAvatarUrl(
    avatarPreview,
    avatarRemoved,
    session.avatarUrl,
  );

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    isSuccess: updateProfile.isSuccess && !form.isSubmitting,
    displayAvatarUrl,
    avatarFileName: avatarFile?.name ?? null,
    avatarError,
    hasAvatar: displayAvatarUrl !== null && displayAvatarUrl.length > 0,
    handleSubmit: form.handleSubmit,
    setFullName: (fullName: string) => form.setValue("fullName", fullName),
    pickAvatar,
    removeAvatar,
  };
}
