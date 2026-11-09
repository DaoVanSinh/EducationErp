import type { InvoiceSummary } from "@/entities/billing";
import { useExtendInvoiceForm } from "@/modules/billing/hooks/use-extend-invoice-form";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ExtendInvoiceDialogProps {
  readonly invoice: InvoiceSummary;
  readonly onClose: () => void;
}

export function ExtendInvoiceDialog({ invoice, onClose }: ExtendInvoiceDialogProps) {
  const form = useExtendInvoiceForm(invoice.id, onClose);

  return (
    <GlassModal
      open
      onClose={onClose}
      title={`Gia hạn học phí ${invoice.studentName ?? ""}`}
      description={`Hạn hiện tại ${formatter.date(invoice.dueDate)}. Tới hạn mới mà chưa đóng thì hoá đơn lại vào danh sách quá hạn.`}
    >
      <form id="extend-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Hạn mới" htmlFor="extend-invoice-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="extend-invoice-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
        <FormField label="Lý do" htmlFor="extend-invoice-reason" error={form.fieldErrors.reason}>
          <GlassInput
            id="extend-invoice-reason"
            value={form.values.reason}
            invalid={form.fieldErrors.reason !== undefined}
            onChange={(event) => form.setValue("reason", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="extend-invoice-form" loading={form.isSubmitting}>
          Gia hạn
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
