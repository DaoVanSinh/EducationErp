import {
  payrollKeys,
  payrollRunApi,
  type CreatePayrollRunPayload,
  type RejectPayrollRunPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useRunsInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: payrollKeys.runs.all });
  };
}

export function useCreatePayrollRun() {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: (payload: CreatePayrollRunPayload) => payrollRunApi.createPayrollRun(payload),
    onSuccess: invalidate,
  });
}

export function useUpdatePayslip(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: ({ payslipId, payload }: { payslipId: string; payload: UpdatePayslipPayload }) =>
      payrollRunApi.updatePayslip(runId, payslipId, payload),
    onSuccess: invalidate,
  });
}

export function useSubmitPayrollRunForApproval(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: () => payrollRunApi.submitForApproval(runId),
    onSuccess: invalidate,
  });
}

export function useApprovePayrollRun(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: () => payrollRunApi.approvePayrollRun(runId),
    onSuccess: invalidate,
  });
}

export function useRejectPayrollRun(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: (payload: RejectPayrollRunPayload) => payrollRunApi.rejectPayrollRun(runId, payload),
    onSuccess: invalidate,
  });
}
