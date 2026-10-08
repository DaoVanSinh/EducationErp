import { enrollmentApi, enrollmentKeys, type CreateEnrollmentPayload } from "@/entities/enrollment";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useEnrollmentsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: enrollmentKeys.all });
  };
}

export function useCreateEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (payload: CreateEnrollmentPayload) => enrollmentApi.createEnrollment(payload),
    onSuccess: invalidate,
  });
}

export function useWithdrawEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (enrollmentId: string) => enrollmentApi.withdrawEnrollment(enrollmentId),
    onSuccess: invalidate,
  });
}

export function useCompleteEnrollment() {
  const invalidate = useEnrollmentsInvalidation();
  return useMutation({
    mutationFn: (enrollmentId: string) => enrollmentApi.completeEnrollment(enrollmentId),
    onSuccess: invalidate,
  });
}
