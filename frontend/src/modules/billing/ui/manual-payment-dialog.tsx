import { useRecordManualPayment } from "@/modules/billing/api/use-billing-mutations";
import { manualPaymentFormSchema } from "@/modules/billing/model/billing-forms";
import { formatter } from "@/shared/lib/format";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface ManualPaymentDialogProps {
  readonly invoiceId: string;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly remaining: number;
}

export function ManualPaymentDialog({ invoiceId, open, onClose, remaining }: ManualPaymentDialogProps) {
  const recordManualPayment = useRecordManualPayment(invoiceId);

  const form = useZodForm({
    schema: manualPaymentFormSchema,
    initialValues: { amount: "" },
    onSubmit: async (values) => {
      await recordManualPayment.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Ghi nhận thanh toán thủ công"
      description="Tiền mặt hoặc chuyển khoản do kế toán nhập tay."
    >
      <form id="manual-payment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Số tiền (VND)"
          htmlFor="manual-payment-amount"
          hint={`Còn lại ${formatter.count(remaining)} đ. Thu quá số còn lại sẽ bị từ chối.`}
          error={form.fieldErrors.amount}
        >
          <GlassInput
            id="manual-payment-amount"
            type="number"
            min={1}
            max={remaining}
            step={1000}
            autoFocus
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="manual-payment-form" loading={form.isSubmitting}>
          Ghi nhận
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
