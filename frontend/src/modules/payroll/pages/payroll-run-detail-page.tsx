import { Can, RequirePermission } from "@/entities/permission";
import { PAYROLL_RUN_STATUS, PAYROLL_RUN_STATUS_LABEL, type PayrollRunDetail } from "@/entities/payroll";
import { usePayrollRunDetailController } from "@/modules/payroll/hooks/use-payroll-run-detail-controller";
import { AdjustPayslipDialog } from "@/modules/payroll/ui/adjust-payslip-dialog";
import { BranchPayrollProgress } from "@/modules/payroll/ui/branch-payroll-progress";
import { PayrollFormulaGuide } from "@/modules/payroll/ui/payroll-formula-guide";
import {
  MissingPayTermsNotice,
  RejectionNotice,
  SubmitBlockedNotice,
} from "@/modules/payroll/ui/payroll-run-notices";
import { PayrollRunTotals } from "@/modules/payroll/ui/payroll-run-summary";
import { PayslipsTable } from "@/modules/payroll/ui/payslips-table";
import { RejectPayrollRunDialog } from "@/modules/payroll/ui/reject-payroll-run-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Wallet } from "lucide-react";
import { useParams } from "react-router-dom";

type Controller = ReturnType<typeof usePayrollRunDetailController>;

export function PayrollRunDetailPage() {
  const { runId = "" } = useParams<{ runId: string }>();
  const controller = usePayrollRunDetailController(runId);

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        {controller.detail.isPending ? <Skeleton className="h-24" /> : null}
        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}
        {controller.detail.data ? (
          <PayrollRunBody runId={runId} data={controller.detail.data} controller={controller} />
        ) : null}
      </div>
    </RequirePermission>
  );
}

function PayrollRunBody({
  runId,
  data,
  controller,
}: {
  readonly runId: string;
  readonly data: PayrollRunDetail;
  readonly controller: Controller;
}) {
  const { run, payslips, missingPayTerms, branchSummaries } = data;
  return (
    <>
      <PageHeader
        title={`Kỳ lương tháng ${run.month}/${run.year}`}
        description={`Từ ${formatter.date(run.periodStart)} đến ${formatter.date(run.periodEnd)} · ${PAYROLL_RUN_STATUS_LABEL[run.status]}`}
        actions={<PayrollRunActions controller={controller} />}
      />

      {controller.actionError ? <ErrorNotice error={controller.actionError} /> : null}
      {run.rejectionReason ? <RejectionNotice reason={run.rejectionReason} /> : null}
      {missingPayTerms.length > 0 ? <MissingPayTermsNotice items={missingPayTerms} /> : null}
      {run.status === PAYROLL_RUN_STATUS.draft && missingPayTerms.length === 0 && controller.blockReason ? (
        <SubmitBlockedNotice reason={controller.blockReason} />
      ) : null}

      <PayrollRunTotals run={run} />

      {run.status === PAYROLL_RUN_STATUS.approved ? <BranchPayrollProgress summaries={branchSummaries} /> : null}

      <GlassPanel>
        {payslips.length === 0 ? (
          <EmptyState
            icon={<Wallet size={28} aria-hidden />}
            title="Kỳ này chưa có phiếu lương"
            description="Phiếu được sinh cho giáo viên lương tháng còn làm việc trong kỳ và giáo viên theo giờ có buổi dạy."
          />
        ) : (
          <PayslipsTable
            rows={payslips}
            periodEnd={run.periodEnd}
            editable={controller.canEdit}
            onAdjust={controller.openAdjustDialog}
          />
        )}
      </GlassPanel>

      <PayrollFormulaGuide />

      <RejectPayrollRunDialog runId={runId} open={controller.rejectDialogOpen} onClose={controller.closeRejectDialog} />
      {controller.adjusting ? (
        <AdjustPayslipDialog
          key={controller.adjusting.id}
          runId={runId}
          payslip={controller.adjusting}
          onClose={controller.closeAdjustDialog}
        />
      ) : null}
    </>
  );
}

function PayrollRunActions({ controller }: { readonly controller: Controller }) {
  return (
    <div className="flex flex-wrap gap-2">
      {controller.canEdit ? (
        <>
          <GlassButton variant="secondary" onClick={controller.onRecalculate} loading={controller.isRecalculating}>
            Tính lại
          </GlassButton>
          <GlassButton
            onClick={controller.onSubmitForApproval}
            loading={controller.isSubmitting}
            disabled={controller.blockReason !== null}
          >
            Gửi duyệt
          </GlassButton>
        </>
      ) : null}
      {controller.isPendingApproval ? (
        <Can {...ACCESS_RULE.approvePayroll}>
          <GlassButton onClick={controller.onApprove} loading={controller.isApproving}>
            Duyệt
          </GlassButton>
          <GlassButton variant="secondary" onClick={controller.openRejectDialog}>
            Từ chối
          </GlassButton>
        </Can>
      ) : null}
    </div>
  );
}
