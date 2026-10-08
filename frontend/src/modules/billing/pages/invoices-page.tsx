import { INVOICE_STATUS, INVOICE_STATUS_LABEL } from "@/entities/billing";
import { Can, RequirePermission } from "@/entities/permission";
import { useInvoicesPageController } from "@/modules/billing/hooks/use-invoices-page-controller";
import { CreateInvoiceDialog } from "@/modules/billing/ui/create-invoice-dialog";
import { InvoicesTable } from "@/modules/billing/ui/invoices-table";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Plus, Receipt } from "lucide-react";

const INVOICE_STATUS_OPTIONS = Object.values(INVOICE_STATUS);

export function InvoicesPage() {
  const controller = useInvoicesPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readInvoice}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Hoá đơn học phí"
          description="Mỗi ghi danh thu tối đa 3 đợt, tổng các đợt không vượt học phí khoá học."
          actions={
            <Can {...ACCESS_RULE.createInvoice}>
              <GlassButton onClick={controller.openCreateDialog} icon={<Plus size={16} aria-hidden />}>
                Tạo đợt thu
              </GlassButton>
            </Can>
          }
        />

        {controller.invoices.isError ? <ErrorNotice error={controller.invoices.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <div className="flex flex-wrap gap-2">
            <GlassSelect
              aria-label="Lọc theo ghi danh"
              value={controller.enrollmentFilter}
              onChange={(event) => controller.setEnrollmentFilter(event.target.value)}
            >
              <option value="">Mọi ghi danh</option>
              {(controller.enrollments.data?.items ?? []).map((enrollment) => (
                <option key={enrollment.id} value={enrollment.id}>
                  {enrollment.id.slice(0, 8)} — HV {enrollment.studentProfileId.slice(0, 8)}
                </option>
              ))}
            </GlassSelect>
            <GlassSelect
              aria-label="Lọc theo trạng thái"
              value={controller.statusFilter}
              onChange={(event) => controller.setStatusFilter(event.target.value)}
            >
              <option value="">Mọi trạng thái</option>
              {INVOICE_STATUS_OPTIONS.map((status) => (
                <option key={status} value={status}>
                  {INVOICE_STATUS_LABEL[status]}
                </option>
              ))}
            </GlassSelect>
          </div>

          {controller.invoices.isPending ? (
            <div className="flex flex-col gap-2">
              <Skeleton className="h-16" />
              <Skeleton className="h-16" />
            </div>
          ) : null}

          {controller.invoices.data ? <InvoicesListSection controller={controller} /> : null}
        </GlassPanel>

        <CreateInvoiceDialog
          open={controller.createDialogOpen}
          onClose={controller.closeCreateDialog}
          enrollments={controller.enrollments.data?.items ?? []}
        />
      </div>
    </RequirePermission>
  );
}

function InvoicesListSection({
  controller,
}: {
  readonly controller: ReturnType<typeof useInvoicesPageController>;
}) {
  const page = controller.invoices.data;
  if (!page || page.items.length === 0) {
    return (
      <EmptyState
        icon={<Receipt size={28} aria-hidden />}
        title="Chưa có hoá đơn nào"
        description="Tạo đợt thu đầu tiên cho một ghi danh đang học."
      />
    );
  }
  return (
    <>
      <InvoicesTable rows={page.items} />
      <Pagination
        page={page.page}
        totalPages={page.totalPages}
        totalItems={page.totalItems}
        onPageChange={controller.setPage}
        itemLabel="hoá đơn"
      />
    </>
  );
}
