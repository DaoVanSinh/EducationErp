import type { BranchPayslip } from "@/entities/payroll";
import { useUndoPayslipPaymentForm } from "@/modules/payroll/hooks/use-pay-payslip-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface UndoPayslipPaymentDialogProps {
  readonly row: BranchPayslip;
  readonly onClose: () => void;
}

export function UndoPayslipPaymentDialog({ row, onClose }: UndoPayslipPaymentDialogProps) {
  const form = useUndoPayslipPaymentForm(row.payslip.id, onClose);

  return (
    <GlassModal
      open
      onClose={onClose}
      title="Huỷ đánh dấu đã trả"
      description={`${row.payslip.accountFullName ?? "Giáo viên"} · Tháng ${row.month}/${row.year}. Phiếu quay về chờ thanh toán, lý do được giữ lại trên phiếu.`}
    >
      <form id="undo-payslip-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Lý do" htmlFor="undo-payslip-reason" error={form.fieldErrors.reason}>
          <GlassInput
            id="undo-payslip-reason"
            autoFocus
            value={form.values.reason}
            invalid={form.fieldErrors.reason !== undefined}
            onChange={(event) => form.setValue("reason", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Đóng
        </GlassButton>
        <GlassButton type="submit" form="undo-payslip-form" loading={form.isSubmitting}>
          Huỷ đánh dấu
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
