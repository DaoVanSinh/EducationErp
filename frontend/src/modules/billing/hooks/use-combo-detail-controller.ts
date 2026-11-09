import { INVOICE_STATUS, useComboDetail } from "@/entities/billing";
import { useCancelCombo, useCreateComboInvoice } from "@/modules/billing/api/use-billing-mutations";
import { useCallback, useMemo } from "react";

/** Toàn bộ state/query/mutation của trang chi tiết combo - ui/* chỉ render (Mandate #2). */
export function useComboDetailController(comboId: string) {
  const detail = useComboDetail(comboId);
  const cancelCombo = useCancelCombo(comboId);
  const createComboInvoice = useCreateComboInvoice();

  const invoices = useMemo(() => detail.data?.invoices ?? [], [detail.data]);
  const liveInvoices = useMemo(
    () => invoices.filter((invoice) => invoice.status !== INVOICE_STATUS.cancelled),
    [invoices],
  );
  const invoicedAmount = liveInvoices.reduce((sum, invoice) => sum + invoice.amount, 0);
  const remainingToInvoice = Math.max((detail.data?.combo.totalDiscountedAmount ?? 0) - invoicedAmount, 0);

  // Backend chặn thêm nếu có giao dịch thất bại còn lưu vết - lỗi đó hiện nguyên văn.
  const canCancel = detail.data !== undefined && invoices.every((invoice) => invoice.amountPaid === 0);

  const onCancelCombo = useCallback(() => {
    void cancelCombo.mutateAsync();
  }, [cancelCombo]);
  const onReissueInvoice = useCallback(() => {
    void createComboInvoice.mutateAsync({ comboId });
  }, [createComboInvoice, comboId]);

  return {
    detail,
    invoicedAmount,
    remainingToInvoice,
    canReissue: detail.data !== undefined && remainingToInvoice > 0,
    canCancel,
    onReissueInvoice,
    isReissuing: createComboInvoice.isPending,
    onCancelCombo,
    isCancelling: cancelCombo.isPending,
    actionError: cancelCombo.error ?? createComboInvoice.error,
  };
}
