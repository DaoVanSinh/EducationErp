import { RequirePermission } from "@/entities/permission";
import {
  PAID_FILTER,
  useBranchPayslipsPageController,
} from "@/modules/payroll/hooks/use-branch-payslips-page-controller";
import { BranchPayslipsTable } from "@/modules/payroll/ui/branch-payslips-table";
import { PayPayslipDialog } from "@/modules/payroll/ui/pay-payslip-dialog";
import { UndoPayslipPaymentDialog } from "@/modules/payroll/ui/undo-payslip-payment-dialog";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { EmptyState } from "@/shared/ui/empty-state";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { GlassSelect } from "@/shared/ui/glass-select";
import { PageHeader } from "@/shared/ui/page-header";
import { Pagination } from "@/shared/ui/pagination";
import { Skeleton } from "@/shared/ui/skeleton";
import { Banknote } from "lucide-react";

type Controller = ReturnType<typeof useBranchPayslipsPageController>;

export function BranchPayslipsPage() {
  const controller = useBranchPayslipsPageController();

  return (
    <RequirePermission {...ACCESS_RULE.readBranchPayslips}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Chi trả lương"
          description="Phiếu lương đã được admin gốc duyệt, chuyển xuống chi nhánh chủ quản của giáo viên. Chi nhánh trả đủ một lần rồi đánh dấu đã thanh toán, không sửa được số tiền."
        />

        {controller.payslips.isError ? <ErrorNotice error={controller.payslips.error} /> : null}

        <GlassPanel className="flex flex-col gap-4">
          <div className="flex flex-wrap items-center gap-2">
            <div className="w-48">
              <GlassInput
                type="month"
                aria-label="Lọc theo tháng"
                value={controller.period}
                onChange={(event) => controller.setPeriod(event.target.value)}
              />
            </div>
            <GlassSelect
              aria-label="Lọc theo tình trạng"
              value={controller.paidFilter}
              onChange={(event) => controller.setPaidFilter(event.target.value as typeof controller.paidFilter)}
            >
              <option value={PAID_FILTER.all}>Tất cả</option>
              <option value={PAID_FILTER.unpaid}>Chờ thanh toán</option>
              <option value={PAID_FILTER.paid}>Đã thanh toán</option>
            </GlassSelect>
          </div>
          {controller.payslips.isPending ? <Skeleton className="h-24" /> : null}
          <PayslipsSection controller={controller} />
        </GlassPanel>

        {controller.paying ? (
          <PayPayslipDialog key={controller.paying.payslip.id} row={controller.paying} onClose={controller.closePay} />
        ) : null}
        {controller.undoing ? (
          <UndoPayslipPaymentDialog
            key={controller.undoing.payslip.id}
            row={controller.undoing}
            onClose={controller.closeUndo}
          />
        ) : null}
      </div>
    </RequirePermission>
  );
}

function PayslipsSection({ controller }: { readonly controller: Controller }) {
  const data = controller.payslips.data;
  if (!data) {
    return null;
  }
  if (data.items.length === 0) {
    return (
      <EmptyState
        icon={<Banknote size={28} aria-hidden />}
        title="Không có phiếu lương phù hợp"
        description="Phiếu hiện ở đây sau khi admin gốc duyệt kỳ lương."
      />
    );
  }
  return (
    <>
      <BranchPayslipsTable rows={data.items} onPay={controller.openPay} onUndo={controller.openUndo} />
      <Pagination
        page={data.page}
        totalPages={data.totalPages}
        totalItems={data.totalItems}
        onPageChange={controller.setPage}
        itemLabel="phiếu lương"
      />
    </>
  );
}
