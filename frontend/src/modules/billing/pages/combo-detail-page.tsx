import { Can, RequirePermission } from "@/entities/permission";
import { useComboDetailController } from "@/modules/billing/hooks/use-combo-detail-controller";
import { ComboCoursesTable } from "@/modules/billing/ui/combo-courses-table";
import { ComboInvoiceDialog } from "@/modules/billing/ui/combo-invoice-dialog";
import { InvoicesTable } from "@/modules/billing/ui/invoices-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Ban, Plus, Receipt } from "lucide-react";
import { useParams } from "react-router-dom";

export function ComboDetailPage() {
  const { comboId = "" } = useParams<{ comboId: string }>();
  const controller = useComboDetailController(comboId);

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Chi tiết combo"
          description="Các khoá trong combo, số tiền sau giảm giá và các đợt thu đã phát hành."
        />

        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}
        {controller.detail.isPending ? <Skeleton className="h-40" /> : null}

        {controller.detail.data ? (
          <>
            <GlassPanel className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-sm text-mist-100">
                  {controller.detail.data.combo.courseCount} khoá
                </p>
                <Badge tone="accent">-{controller.detail.data.combo.discountPercent}%</Badge>
                <p className="text-xs text-mist-500">Hạn {controller.detail.data.combo.dueDate}</p>
                <p className="text-xs text-mist-500">
                  Đợt {controller.liveInstallmentCount}/{controller.maxInstallments}
                </p>
              </div>
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-4">
                <AmountCell label="Tổng gốc" value={controller.detail.data.combo.totalOriginalAmount} />
                <AmountCell
                  label="Phải thu sau giảm"
                  value={controller.detail.data.combo.totalDiscountedAmount}
                />
                <AmountCell label="Đã phát hành" value={controller.invoicedAmount} />
                <AmountCell label="Chưa phát hành" value={controller.remainingToInvoice} />
              </dl>
              <Can {...ACCESS_RULE.createInvoice}>
                <ComboActions controller={controller} />
              </Can>
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Khoá trong combo</h2>
              <ComboCoursesTable rows={controller.detail.data.enrollments} />
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Các đợt thu</h2>
              <ComboInvoicesSection controller={controller} />
            </GlassPanel>

            <ComboInvoiceDialog
              comboId={comboId}
              open={controller.invoiceDialogOpen}
              onClose={controller.closeInvoiceDialog}
              remainingToInvoice={controller.remainingToInvoice}
              installmentNumber={controller.liveInstallmentCount + 1}
              maxInstallments={controller.maxInstallments}
            />
          </>
        ) : null}
      </div>
    </RequirePermission>
  );
}

function AmountCell({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div className="glass rounded-2xl p-3">
      <dt className="text-xs text-mist-500">{label}</dt>
      <dd className="text-sm text-mist-100">{formatter.count(value)} đ</dd>
    </div>
  );
}

/** Tách ra để trang không rẽ nhánh trong JSX (Mandate #3). */
function ComboActions({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDetailController>;
}) {
  return (
    <div className="flex flex-col gap-2">
      {controller.cancelError ? <ErrorNotice error={controller.cancelError} /> : null}
      <div className="flex flex-wrap gap-2">
        <GlassButton
          size="sm"
          disabled={!controller.canIssueInstallment}
          onClick={controller.openInvoiceDialog}
          icon={<Plus size={14} aria-hidden />}
        >
          Phát hành đợt thu
        </GlassButton>
        <GlassButton
          variant="ghost"
          size="sm"
          disabled={!controller.canCancel || controller.isCancelling}
          onClick={controller.onCancelCombo}
          icon={<Ban size={14} aria-hidden />}
        >
          Huỷ combo
        </GlassButton>
      </div>
      {controller.canCancel ? null : (
        <p className="text-xs text-mist-500">
          Combo đã phát hành hoá đơn nên không huỷ được nữa.
        </p>
      )}
    </div>
  );
}

/** Tái dùng InvoicesTable: một đợt thu của combo là một Invoice bình thường, bấm "Xem chi tiết" là
 * sang đúng trang chi tiết hoá đơn có sẵn để thu online/thủ công (Review Focus #9). */
function ComboInvoicesSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useComboDetailController>;
}) {
  const invoices = controller.detail.data?.invoices ?? [];
  if (invoices.length === 0) {
    return (
      <EmptyState
        icon={<Receipt size={28} aria-hidden />}
        title="Chưa phát hành đợt thu nào"
        description="Phát hành đợt đầu tiên cho combo này; tối đa 3 đợt, tổng không vượt số tiền sau giảm."
      />
    );
  }
  return <InvoicesTable rows={invoices} />;
}
