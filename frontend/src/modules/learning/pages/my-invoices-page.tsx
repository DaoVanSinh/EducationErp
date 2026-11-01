import { RequirePermission } from "@/entities/permission";
import { useMyInvoicesPageController } from "@/modules/learning/hooks/use-my-invoices-page-controller";
import type { TuitionTotals } from "@/modules/learning/model/learning-rules";
import { InvoiceCard } from "@/modules/learning/ui/invoice-card";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { Receipt } from "lucide-react";

export function MyInvoicesPage() {
  return (
    <RequirePermission {...ACCESS_RULE.readLearning}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học phí của tôi"
          description="Các đợt học phí, số tiền đã đóng, còn lại và lịch sử thanh toán. Thanh toán online qua VNPay hoặc đóng tiền mặt tại quầy."
        />
        <MyInvoicesContent />
      </div>
    </RequirePermission>
  );
}

function MyInvoicesContent() {
  const controller = useMyInvoicesPageController();

  if (controller.error) {
    return <ErrorNotice error={controller.error} />;
  }
  if (controller.isPending) {
    return <Skeleton className="h-48" />;
  }
  if (controller.isEmpty) {
    return (
      <GlassPanel>
        <EmptyState
          icon={<Receipt size={28} aria-hidden />}
          title="Chưa có hoá đơn học phí nào"
          description="Hoá đơn sẽ hiện ở đây khi trung tâm phát hành học phí cho lớp bạn đã ghi danh."
        />
      </GlassPanel>
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <TotalsPanel totals={controller.totals} />
      {controller.payError ? <ErrorNotice error={controller.payError} /> : null}
      {controller.invoices.map((detail) => (
        <InvoiceCard
          key={detail.invoice.id}
          detail={detail}
          courseName={controller.courseNameOf(detail.invoice.courseId)}
          onPayVnpay={controller.onPayVnpay}
          isPaying={controller.payingInvoiceId === detail.invoice.id}
        />
      ))}
    </div>
  );
}

function TotalsPanel({ totals }: { readonly totals: TuitionTotals }) {
  return (
    <GlassPanel className="grid grid-cols-1 gap-3 sm:grid-cols-3">
      <Total label="Tổng học phí" value={totals.total} />
      <Total label="Đã đóng" value={totals.paid} />
      <Total label="Còn phải đóng" value={totals.remaining} />
    </GlassPanel>
  );
}

function Total({ label, value }: { readonly label: string; readonly value: number }) {
  return (
    <div>
      <p className="text-xs text-mist-400">{label}</p>
      <p className="text-lg font-semibold text-mist-100">{formatter.money(value)}</p>
    </div>
  );
}
