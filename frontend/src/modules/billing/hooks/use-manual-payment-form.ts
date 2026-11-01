import { PAYMENT_METHOD, type OfflinePaymentMethod } from "@/entities/billing";
import { useRecordManualPayment } from "@/modules/billing/api/use-billing-mutations";
import { manualPaymentFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useCallback } from "react";

/** Gợi ý nội dung ghi chú theo kênh thu - kế toán biết nên ghi gì để sau này đối soát. */
const NOTE_PLACEHOLDER: Record<OfflinePaymentMethod, string> = {
  [PAYMENT_METHOD.cash]: "Số phiếu thu, ví dụ PT-2026-0012",
  [PAYMENT_METHOD.bankTransfer]: "Ngân hàng + mã giao dịch, ví dụ VCB 123456789",
};

/**
 * Form ghi nhận thu tay: số tiền, kênh thu (tiền mặt / chuyển khoản), ghi chú đối soát. Mặc định là
 * tiền mặt - kênh phổ biến nhất ở quầy.
 */
export function useManualPaymentForm(invoiceId: string, onClose: () => void) {
  const recordManualPayment = useRecordManualPayment(invoiceId);

  const form = useZodForm({
    schema: manualPaymentFormSchema,
    initialValues: { amount: "", method: PAYMENT_METHOD.cash, note: "" },
    onSubmit: async ({ amount, method, note }) => {
      await recordManualPayment.mutateAsync({ amount, method, note: note === "" ? undefined : note });
      form.reset();
      onClose();
    },
  });

  const selectMethod = useCallback((method: OfflinePaymentMethod) => form.setValue("method", method), [form]);

  return {
    form,
    selectMethod,
    notePlaceholder: NOTE_PLACEHOLDER[form.values.method],
  };
}
