import { INVOICE_STATUS_LABEL } from "@/entities/billing";
import { Can, RequirePermission } from "@/entities/permission";
import { useInvoiceDetailController } from "@/modules/billing/hooks/use-invoice-detail-controller";
import { ManualPaymentDialog } from "@/modules/billing/ui/manual-payment-dialog";
import { OnlinePaymentPicker } from "@/modules/billing/ui/online-payment-picker";
import { PaymentsTable } from "@/modules/billing/ui/payments-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Ban, Banknote } from "lucide-react";
import { useParams } from "react-router-dom";

export function InvoiceDetailPage() {
  const { invoiceId = "" } = useParams<{ invoiceId: string }>();
  const controller = useInvoiceDetailController(invoiceId);

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader title="Chi tiết hoá đơn" description="Số dư còn lại, các khoản đã thu và lịch sử giao dịch." />

        {controller.detail.isError ? <ErrorNotice error={controller.detail.error} /> : null}
        {controller.detail.isPending ? <Skeleton className="h-40" /> : null}

        {controller.detail.data ? (
          <>
            <GlassPanel className="flex flex-col gap-4">
              <div className="flex flex-wrap items-center gap-3">
                <p className="text-sm text-mist-100">Đợt {controller.detail.data.invoice.installmentNumber}</p>
                <Badge tone={controller.isPayable ? "neutral" : "positive"}>
                  {INVOICE_STATUS_LABEL[controller.detail.data.invoice.status]}
                </Badge>
                <p className="text-xs text-mist-500">
                  Hạn {controller.detail.data.invoice.dueDate}
                </p>
              </div>
              <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <AmountCell label="Số tiền đợt này" value={controller.detail.data.invoice.amount} />
                <AmountCell label="Đã thu" value={controller.detail.data.invoice.amountPaid} />
                <AmountCell label="Còn lại" value={controller.remaining} />
              </dl>
              <Can {...ACCESS_RULE.updateInvoice}>
                <InvoiceActions controller={controller} />
              </Can>
            </GlassPanel>

            <GlassPanel className="flex flex-col gap-4">
              <h2 className="text-sm font-semibold text-mist-100">Lịch sử thanh toán</h2>
              <PaymentsTable rows={controller.detail.data.payments} />
            </GlassPanel>

            <ManualPaymentDialog
              invoiceId={invoiceId}
              open={controller.manualDialogOpen}
              onClose={controller.closeManualDialog}
              remaining={controller.remaining}
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

/** Ba nút chỉ có nghĩa khi hoá đơn còn thu được - tách ra để trang không rẽ nhánh trong JSX. */
function InvoiceActions({
  controller,
}: {
  readonly controller: ReturnType<typeof useInvoiceDetailController>;
}) {
  if (!controller.isPayable) {
    return <p className="text-xs text-mist-500">Hoá đơn đã chốt, không còn thao tác thu tiền.</p>;
  }
  return (
    <div className="flex flex-col gap-2">
      {controller.onlinePaymentError ? <ErrorNotice error={controller.onlinePaymentError} /> : null}
      <div className="flex flex-wrap gap-2">
        <OnlinePaymentPicker onPay={controller.onPayOnline} isPaying={controller.isPayingOnline} />
        <GlassButton
          variant="secondary"
          size="sm"
          onClick={controller.openManualDialog}
          icon={<Banknote size={14} aria-hidden />}
        >
          Ghi nhận thanh toán thủ công
        </GlassButton>
        <GlassButton
          variant="ghost"
          size="sm"
          disabled={!controller.canCancel || controller.isCancelling}
          onClick={controller.onCancel}
          icon={<Ban size={14} aria-hidden />}
        >
          Huỷ hoá đơn
        </GlassButton>
      </div>
    </div>
  );
}
