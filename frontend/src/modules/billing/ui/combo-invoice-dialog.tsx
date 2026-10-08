import { useCreateComboInvoice } from "@/modules/billing/api/use-billing-mutations";
import { comboInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { formatter } from "@/shared/lib/format";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ComboInvoiceDialogProps {
  readonly comboId: string;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly remainingToInvoice: number;
  readonly installmentNumber: number;
  readonly maxInstallments: number;
}

export function ComboInvoiceDialog({
  comboId,
  open,
  onClose,
  remainingToInvoice,
  installmentNumber,
  maxInstallments,
}: ComboInvoiceDialogProps) {
  const createComboInvoice = useCreateComboInvoice();

  const form = useZodForm({
    schema: comboInvoiceFormSchema,
    initialValues: { amount: "", dueDate: "" },
    onSubmit: async (values) => {
      await createComboInvoice.mutateAsync({ comboId, amount: values.amount, dueDate: values.dueDate });
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Phát hành đợt ${installmentNumber}/${maxInstallments}`}
      description="Tổng các đợt của combo không vượt số tiền sau giảm giá."
    >
      <form id="combo-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số tiền đợt này (VND)"
          htmlFor="combo-invoice-amount"
          hint={`Còn có thể phát hành ${formatter.count(remainingToInvoice)} đ.`}
          error={form.fieldErrors.amount}
        >
          <GlassInput
            id="combo-invoice-amount"
            type="number"
            min={1}
            max={remainingToInvoice}
            step={1000}
            autoFocus
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>

        <FormField label="Hạn thanh toán" htmlFor="combo-invoice-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="combo-invoice-due-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="combo-invoice-form" loading={form.isSubmitting}>
          Phát hành
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
