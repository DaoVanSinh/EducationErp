import type { InvoiceSummary } from "@/entities/billing";
import { Can, RequirePermission } from "@/entities/permission";
import { useOverdueInvoicesController } from "@/modules/billing/hooks/use-overdue-invoices-controller";
import { ExtendInvoiceDialog } from "@/modules/billing/ui/extend-invoice-dialog";
import { InvoicesTable } from "@/modules/billing/ui/invoices-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ConfirmDialog } from "@/shared/ui/confirm-dialog";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { CalendarClock, CheckCircle2, UserX } from "lucide-react";

type Controller = ReturnType<typeof useOverdueInvoicesController>;

export function OverdueInvoicesPage() {
  const controller = useOverdueInvoicesController();
  const { invoices, extending, suspending } = controller;

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Học phí quá hạn"
          description="Học viên chưa đóng đủ sau ngày thứ 7 kể từ khai giảng. Admin gốc gia hạn kèm lý do; admin tạm đóng tài khoản nếu cần. Hệ thống không tự rút lớp."
        />

        {invoices.isError ? <ErrorNotice error={invoices.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          {invoices.isPending ? <Skeleton className="h-24" /> : null}
          <OverdueList controller={controller} />
        </GlassPanel>

        {extending ? (
          <ExtendInvoiceDialog key={extending.id} invoice={extending} onClose={controller.closeExtend} />
        ) : null}
        {suspending ? (
          <ConfirmDialog
            open
            title={`Tạm đóng tài khoản ${suspending.studentName ?? "học viên"}`}
            description="Học viên không đăng nhập được cho tới khi mở lại ở màn Tài khoản. Lớp học, điểm và hoá đơn giữ nguyên."
            confirmLabel="Tạm đóng"
            tone="danger"
            isPending={controller.isSuspending}
            error={controller.suspendError}
            onConfirm={controller.confirmSuspend}
            onClose={controller.closeSuspend}
          />
        ) : null}
      </div>
    </RequirePermission>
  );
}

function OverdueList({ controller }: { readonly controller: Controller }) {
  const data = controller.invoices.data;
  if (!data) {
    return null;
  }
  if (data.items.length === 0) {
    return (
      <EmptyState
        icon={<CheckCircle2 size={28} aria-hidden />}
        title="Không có học phí quá hạn"
        description="Hoá đơn quá hạn sẽ hiện ở đây sau khi tác vụ hằng ngày quét xong."
      />
    );
  }
  return (
    <>
      <InvoicesTable rows={data.items} renderActions={(invoice) => <OverdueActions invoice={invoice} controller={controller} />} />
      <Pagination
        page={data.page}
        totalPages={data.totalPages}
        totalItems={data.totalItems}
        onPageChange={controller.setPage}
        itemLabel="hoá đơn"
      />
    </>
  );
}

function OverdueActions({
  invoice,
  controller,
}: {
  readonly invoice: InvoiceSummary;
  readonly controller: Controller;
}) {
  return (
    <>
      <Can {...ACCESS_RULE.extendInvoice}>
        <GlassButton
          variant="secondary"
          size="sm"
          onClick={() => controller.openExtend(invoice)}
          icon={<CalendarClock size={14} aria-hidden />}
        >
          Gia hạn
        </GlassButton>
      </Can>
      {invoice.studentAccountId === null ? null : (
        <Can {...ACCESS_RULE.manageAccountLifecycle}>
          <GlassButton
            variant="ghost"
            size="sm"
            onClick={() => controller.openSuspend(invoice)}
            icon={<UserX size={14} aria-hidden />}
          >
            Tạm đóng tài khoản
          </GlassButton>
        </Can>
      )}
    </>
  );
}
