import { usePermissions } from "@/entities/permission";
import { PAYROLL_RUN_STATUS, usePayrollRunDetail, type Payslip } from "@/entities/payroll";
import {
  useApprovePayrollRun,
  useRecalculatePayrollRun,
  useSubmitPayrollRunForApproval,
} from "@/modules/payroll/api/use-payroll-run-mutations";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useCallback, useState } from "react";

/** Lý do chưa gửi duyệt được, để nút tắt kèm giải thích thay vì bấm rồi mới báo lỗi. Hết lý do thì trả null. */
function submitBlockReason(periodEnd: string, unpaidTeacherCount: number): string | null {
  if (todayLocalIsoDate() <= periodEnd) {
    return "Tháng chưa kết thúc nên giờ dạy chưa đủ. Kỳ lương gửi duyệt được sau ngày cuối tháng.";
  }
  if (unpaidTeacherCount > 0) {
    return "Còn giáo viên đã có buổi dạy nhưng chưa thiết lập lương.";
  }
  return null;
}

/** Toàn bộ state/mutation của trang chi tiết kỳ lương - ui/payroll-run-detail-page.tsx chỉ render. */
export function usePayrollRunDetailController(runId: string) {
  const permissions = usePermissions();
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [adjusting, setAdjusting] = useState<Payslip | null>(null);
  const detail = usePayrollRunDetail(runId);
  const recalculate = useRecalculatePayrollRun(runId);
  const submitForApproval = useSubmitPayrollRunForApproval(runId);
  const approve = useApprovePayrollRun(runId);

  const run = detail.data?.run;
  const isDraft = run?.status === PAYROLL_RUN_STATUS.draft;
  const isPendingApproval = run?.status === PAYROLL_RUN_STATUS.pendingApproval;
  const canEdit = isDraft && permissions.allows(ACCESS_RULE.updatePayroll);
  const unpaidTeacherCount = detail.data?.missingPayTerms.filter((item) => item.sessionCount > 0).length ?? 0;
  const blockReason = run === undefined ? null : submitBlockReason(run.periodEnd, unpaidTeacherCount);

  const onRecalculate = useCallback(() => {
    recalculate.mutate();
  }, [recalculate]);
  const onSubmitForApproval = useCallback(() => {
    submitForApproval.mutate();
  }, [submitForApproval]);
  const onApprove = useCallback(() => {
    approve.mutate();
  }, [approve]);

  const openRejectDialog = useCallback(() => setRejectDialogOpen(true), []);
  const closeRejectDialog = useCallback(() => setRejectDialogOpen(false), []);
  const closeAdjustDialog = useCallback(() => setAdjusting(null), []);

  return {
    detail,
    isPendingApproval,
    canEdit,
    blockReason,
    actionError: recalculate.error ?? submitForApproval.error ?? approve.error,
    onRecalculate,
    isRecalculating: recalculate.isPending,
    onSubmitForApproval,
    isSubmitting: submitForApproval.isPending,
    onApprove,
    isApproving: approve.isPending,
    rejectDialogOpen,
    openRejectDialog,
    closeRejectDialog,
    adjusting,
    openAdjustDialog: setAdjusting,
    closeAdjustDialog,
  };
}
