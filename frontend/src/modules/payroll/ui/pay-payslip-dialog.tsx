import { PAYSLIP_PAYMENT_METHOD_LABEL, type BranchPayslip, type PayslipPaymentMethod } from "@/entities/payroll";
import { usePayPayslipForm } from "@/modules/payroll/hooks/use-pay-payslip-form";
import { formatter } from "@/shared/lib/format";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

const METHODS = Object.keys(PAYSLIP_PAYMENT_METHOD_LABEL) as PayslipPaymentMethod[];

export interface PayPayslipDialogProps {
  readonly row: BranchPayslip;
  readonly onClose: () => void;
}

/** Trả đủ một lần: số tiền đã chốt khi duyệt, không sửa được. */
export function PayPayslipDialog({ row, onClose }: PayPayslipDialogProps) {
  const form = usePayPayslipForm(row.payslip.id, onClose);

  return (
    <GlassModal
      open
      onClose={onClose}
      title={`Trả lương ${row.payslip.accountFullName ?? "giáo viên"}`}
      description={`Tháng ${row.month}/${row.year} · Thực nhận ${formatter.money(row.payslip.netPay)}`}
    >
      <form id="pay-payslip-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ngày trả" htmlFor="pay-payslip-date" error={form.fieldErrors.paidOn}>
          <GlassInput
            id="pay-payslip-date"
            type="date"
            max={todayLocalIsoDate()}
            value={form.values.paidOn}
            invalid={form.fieldErrors.paidOn !== undefined}
            onChange={(event) => form.setValue("paidOn", event.target.value)}
          />
        </FormField>

        <FormField label="Hình thức" htmlFor="pay-payslip-method">
          <GlassSelect
            id="pay-payslip-method"
            containerClassName="w-full"
            value={form.values.method}
            onChange={(event) => form.setValue("method", event.target.value as PayslipPaymentMethod)}
          >
            {METHODS.map((method) => (
              <option key={method} value={method}>
                {PAYSLIP_PAYMENT_METHOD_LABEL[method]}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Ghi chú (không bắt buộc)" htmlFor="pay-payslip-note" error={form.fieldErrors.note}>
          <GlassInput
            id="pay-payslip-note"
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
        <GlassButton type="submit" form="pay-payslip-form" loading={form.isSubmitting}>
          Xác nhận đã trả
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
