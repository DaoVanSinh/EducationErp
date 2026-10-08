import { Can, RequirePermission } from "@/entities/permission";
import { PAYROLL_RUN_STATUS, PAYROLL_RUN_STATUS_LABEL } from "@/entities/payroll";
import { usePayrollRunDetailController } from "@/modules/payroll/hooks/use-payroll-run-detail-controller";
import { PayslipsTable } from "@/modules/payroll/ui/payslips-table";
import { RejectPayrollRunDialog } from "@/modules/payroll/ui/reject-payroll-run-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { useParams } from "react-router-dom";

export function PayrollRunDetailPage() {
  const { runId = "" } = useParams<{ runId: string }>();
  const controller = usePayrollRunDetailController(runId);

  return (
    <RequirePermission {...ACCESS_RULE.readPayroll}>
      <div className="flex flex-col gap-6">
        {controller.detail.isPending ? <Skeleton className="h-24" /> : null}
        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}

        {controller.detail.data ? (
          <>
            <PageHeader
              title={`Kỳ lương ${controller.detail.data.run.month}/${controller.detail.data.run.year}`}
              description={PAYROLL_RUN_STATUS_LABEL[controller.detail.data.run.status]}
              actions={
                <div className="flex gap-2">
                  {controller.isDraft ? (
                    <GlassButton onClick={controller.onSubmitForApproval} loading={controller.isSubmitting}>
                      Gửi duyệt
                    </GlassButton>
                  ) : null}
                  <Can {...ACCESS_RULE.approvePayroll}>
                    {controller.detail.data.run.status === PAYROLL_RUN_STATUS.pendingApproval ? (
                      <>
                        <GlassButton onClick={controller.onApprove} loading={controller.isApproving}>
                          Duyệt
                        </GlassButton>
                        <GlassButton variant="secondary" onClick={controller.openRejectDialog}>
                          Từ chối
                        </GlassButton>
                      </>
                    ) : null}
                  </Can>
                </div>
              }
            />

            <GlassPanel>
              <PayslipsTable
                rows={controller.detail.data.payslips}
                editable={controller.isDraft}
                onUpdate={(payslipId, payload) => controller.updatePayslip.mutate({ payslipId, payload })}
              />
            </GlassPanel>

            <RejectPayrollRunDialog runId={runId} open={controller.rejectDialogOpen} onClose={controller.closeRejectDialog} />
          </>
        ) : null}
      </div>
    </RequirePermission>
  );
}
