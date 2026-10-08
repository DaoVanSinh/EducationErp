import { PAYROLL_RUN_STATUS, usePayrollRunDetail } from "@/entities/payroll";
import {
  useApprovePayrollRun,
  useRejectPayrollRun,
  useSubmitPayrollRunForApproval,
  useUpdatePayslip,
} from "@/modules/payroll/api/use-payroll-run-mutations";
import { useCallback, useState } from "react";

/** Toàn bộ state/mutation của trang chi tiết kỳ lương - ui/payroll-run-detail-page.tsx chỉ render. */
export function usePayrollRunDetailController(runId: string) {
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const detail = usePayrollRunDetail(runId);
  const updatePayslip = useUpdatePayslip(runId);
  const submitForApproval = useSubmitPayrollRunForApproval(runId);
  const approve = useApprovePayrollRun(runId);
  const reject = useRejectPayrollRun(runId);

  const isDraft = detail.data?.run.status === PAYROLL_RUN_STATUS.draft;

  const onSubmitForApproval = useCallback(() => {
    void submitForApproval.mutateAsync();
  }, [submitForApproval]);

  const onApprove = useCallback(() => {
    void approve.mutateAsync();
  }, [approve]);

  const openRejectDialog = useCallback(() => setRejectDialogOpen(true), []);
  const closeRejectDialog = useCallback(() => setRejectDialogOpen(false), []);

  return {
    detail,
    isDraft,
    updatePayslip,
    onSubmitForApproval,
    isSubmitting: submitForApproval.isPending,
    onApprove,
    isApproving: approve.isPending,
    reject,
    rejectDialogOpen,
    openRejectDialog,
    closeRejectDialog,
  };
}
