import {
  payrollKeys,
  payrollRunApi,
  payslipApi,
  type CreatePayrollRunPayload,
  type MarkPayslipPaidPayload,
  type UndoPayslipPaymentPayload,
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

export function useRecalculatePayrollRun(runId: string) {
  const invalidate = useRunsInvalidation();
  return useMutation({
    mutationFn: () => payrollRunApi.recalculate(runId),
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

/** Đánh dấu đã trả làm đổi cả tiến độ của kỳ lương, nên dọn luôn cache kỳ. */
function usePaymentInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: payrollKeys.payslips.all }),
      queryClient.invalidateQueries({ queryKey: payrollKeys.runs.all }),
    ]);
  };
}

export function useMarkPayslipPaid(payslipId: string) {
  const invalidate = usePaymentInvalidation();
  return useMutation({
    mutationFn: (payload: MarkPayslipPaidPayload) => payslipApi.markPaid(payslipId, payload),
    onSuccess: invalidate,
  });
}

export function useUndoPayslipPayment(payslipId: string) {
  const invalidate = usePaymentInvalidation();
  return useMutation({
    mutationFn: (payload: UndoPayslipPaymentPayload) => payslipApi.undoPayment(payslipId, payload),
    onSuccess: invalidate,
  });
}
