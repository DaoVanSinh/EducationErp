import {
  MAX_PAYMENT_NOTE_LENGTH,
  OFFLINE_PAYMENT_METHODS,
  PAYMENT_METHOD,
  PAYMENT_METHOD_LABEL,
  type OfflinePaymentMethod,
} from "@/entities/billing";
import { useManualPaymentForm } from "@/modules/billing/hooks/use-manual-payment-form";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { SegmentedTabs, type SegmentedTab } from "@/shared/ui/segmented-tabs";
import { Banknote, Landmark } from "lucide-react";
import type { ReactNode } from "react";

export interface ManualPaymentDialogProps {
  readonly invoiceId: string;
  readonly open: boolean;
  readonly onClose: () => void;
  readonly remaining: number;
}

const METHOD_ICON: Record<OfflinePaymentMethod, ReactNode> = {
  [PAYMENT_METHOD.cash]: <Banknote size={15} aria-hidden />,
  [PAYMENT_METHOD.bankTransfer]: <Landmark size={15} aria-hidden />,
};

const METHOD_TABS: readonly SegmentedTab<OfflinePaymentMethod>[] = OFFLINE_PAYMENT_METHODS.map((method) => ({
  key: method,
  label: PAYMENT_METHOD_LABEL[method],
  icon: METHOD_ICON[method],
}));

export function ManualPaymentDialog({ invoiceId, open, onClose, remaining }: ManualPaymentDialogProps) {
  const { form, selectMethod, notePlaceholder } = useManualPaymentForm(invoiceId, onClose);

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Ghi nhận thu tiền"
      description="Tiền mặt tại quầy hoặc chuyển khoản do kế toán đối chiếu rồi nhập."
    >
      <form id="manual-payment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Hình thức thu" htmlFor="manual-payment-method">
          <SegmentedTabs tabs={METHOD_TABS} activeTab={form.values.method} onTabChange={selectMethod} />
        </FormField>

        <FormField
          label="Số tiền (VND)"
          htmlFor="manual-payment-amount"
          hint={`Còn lại ${formatter.money(remaining)}. Thu quá số còn lại sẽ bị từ chối.`}
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

        <FormField
          label="Ghi chú (không bắt buộc)"
          htmlFor="manual-payment-note"
          hint="Dùng để đối soát sau này."
          error={form.fieldErrors.note}
        >
          <GlassInput
            id="manual-payment-note"
            maxLength={MAX_PAYMENT_NOTE_LENGTH}
            placeholder={notePlaceholder}
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
        <GlassButton type="submit" form="manual-payment-form" loading={form.isSubmitting}>
          Ghi nhận
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
