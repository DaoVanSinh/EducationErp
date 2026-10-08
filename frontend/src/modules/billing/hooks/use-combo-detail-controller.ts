import { INVOICE_STATUS, useComboDetail } from "@/entities/billing";
import { useCancelCombo } from "@/modules/billing/api/use-billing-mutations";
import { useCallback, useMemo, useState } from "react";

/** Khớp BillingConstants.Limits.MAX_INSTALLMENTS_PER_COMBO - 3 đợt cho CẢ combo. */
const MAX_INSTALLMENTS_PER_COMBO = 3;

/** Toàn bộ state/query/mutation của trang chi tiết combo - ui/* chỉ render (Mandate #2). */
export function useComboDetailController(comboId: string) {
  const [invoiceDialogOpen, setInvoiceDialogOpen] = useState(false);
  const detail = useComboDetail(comboId);
  const cancelCombo = useCancelCombo(comboId);

  const invoices = useMemo(() => detail.data?.invoices ?? [], [detail.data]);

  // Đợt đã huỷ không chiếm chỗ và không tính vào tổng - đúng như backend tính (CreateComboInvoice).
  const liveInvoices = useMemo(
    () => invoices.filter((invoice) => invoice.status !== INVOICE_STATUS.cancelled),
    [invoices],
  );
  const invoicedAmount = liveInvoices.reduce((sum, invoice) => sum + invoice.amount, 0);
  const discountedTotal = detail.data?.combo.totalDiscountedAmount ?? 0;
  const remainingToInvoice = Math.max(discountedTotal - invoicedAmount, 0);

  const canIssueInstallment =
    detail.data !== undefined
    && liveInvoices.length < MAX_INSTALLMENTS_PER_COMBO
    && remainingToInvoice > 0;
  // Backend chỉ cho huỷ khi combo chưa có hoá đơn nào, KỂ CẢ hoá đơn đã huỷ (Review Focus #6).
  const canCancel = detail.data !== undefined && invoices.length === 0;

  const openInvoiceDialog = useCallback(() => setInvoiceDialogOpen(true), []);
  const closeInvoiceDialog = useCallback(() => setInvoiceDialogOpen(false), []);

  const onCancelCombo = useCallback(() => {
    void cancelCombo.mutateAsync();
  }, [cancelCombo]);

  return {
    detail,
    liveInstallmentCount: liveInvoices.length,
    maxInstallments: MAX_INSTALLMENTS_PER_COMBO,
    invoicedAmount,
    remainingToInvoice,
    canIssueInstallment,
    canCancel,
    invoiceDialogOpen,
    openInvoiceDialog,
    closeInvoiceDialog,
    onCancelCombo,
    isCancelling: cancelCombo.isPending,
    cancelError: cancelCombo.error,
  };
}
