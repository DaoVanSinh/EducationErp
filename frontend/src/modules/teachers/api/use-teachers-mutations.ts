import { teacherApi, teacherKeys, type CreateTeacherProfilePayload, type UpdateTeacherProfilePayload } from "@/entities/teacher";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useTeachersInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: teacherKeys.all });
  };
}

export function useCreateTeacherProfile() {
  const invalidate = useTeachersInvalidation();
  return useMutation({
    mutationFn: (payload: CreateTeacherProfilePayload) => teacherApi.createProfile(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateTeacherProfile(profileId: string) {
  const invalidate = useTeachersInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateTeacherProfilePayload) => teacherApi.updateProfile(profileId, payload),
    onSuccess: invalidate,
  });
}
