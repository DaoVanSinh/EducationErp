import type { Payslip } from "@/entities/payroll";
import { useAdjustPayslipController } from "@/modules/payroll/hooks/use-adjust-payslip-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface AdjustPayslipDialogProps {
  readonly runId: string;
  readonly payslip: Payslip;
  readonly onClose: () => void;
}

export function AdjustPayslipDialog({ runId, payslip, onClose }: AdjustPayslipDialogProps) {
  const form = useAdjustPayslipController({ runId, payslip, onClose });

  return (
    <GlassModal
      open
      onClose={onClose}
      title={`Thưởng/điều chỉnh: ${payslip.accountFullName ?? "giáo viên"}`}
      description="Khoản này tính vào thu nhập, nên thuế được tính lại theo. Tính lại kỳ lương không làm mất khoản này."
    >
      <form id="adjust-payslip-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField
          label="Số tiền (đồng)"
          htmlFor="adjust-amount"
          hint="Số dương là cộng thêm, số âm là trừ đi, nhập 0 để bỏ."
          error={form.fieldErrors.adjustment}
        >
          <GlassInput
            id="adjust-amount"
            type="number"
            step={1000}
            autoFocus
            value={form.values.adjustment}
            invalid={form.fieldErrors.adjustment !== undefined}
            onChange={(event) => form.setValue("adjustment", event.target.value)}
          />
        </FormField>
        <FormField label="Lý do" htmlFor="adjust-note" error={form.fieldErrors.note}>
          <GlassInput
            id="adjust-note"
            value={form.values.note}
            invalid={form.fieldErrors.note !== undefined}
            onChange={(event) => form.setValue("note", event.target.value)}
          />
        </FormField>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="adjust-payslip-form" loading={form.isSubmitting}>
          Lưu
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
