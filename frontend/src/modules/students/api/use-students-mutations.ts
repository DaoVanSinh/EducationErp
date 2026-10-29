import { studentApi, studentKeys, type CreateStudentPayload, type UpdateStudentProfilePayload } from "@/entities/student";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useStudentsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: studentKeys.all });
  };
}

export function useCreateStudent() {
  const invalidate = useStudentsInvalidation();
  return useMutation({
    mutationFn: (payload: CreateStudentPayload) => studentApi.createProfile(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateStudentProfile(profileId: string) {
  const invalidate = useStudentsInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateStudentProfilePayload) => studentApi.updateProfile(profileId, payload),
    onSuccess: invalidate,
  });
}
