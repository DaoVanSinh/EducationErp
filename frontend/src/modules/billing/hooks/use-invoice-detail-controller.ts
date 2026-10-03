import { INVOICE_STATUS, useInvoiceDetail, type OnlineGateway } from "@/entities/billing";
import {
  useCancelInvoice,
  useInitiateOnlinePayment,
} from "@/modules/billing/api/use-billing-mutations";
import { useCallback, useState } from "react";

/** Trạng thái còn thu được tiền - khớp BillingRules.isPayable ở backend (UNPAID/PARTIALLY_PAID/OVERDUE). */
const PAYABLE_STATUSES: readonly string[] = [
  INVOICE_STATUS.unpaid,
  INVOICE_STATUS.partiallyPaid,
  INVOICE_STATUS.overdue,
];

/** Toàn bộ state/mutation của trang chi tiết hoá đơn - ui/* chỉ render (Mandate #2). */
export function useInvoiceDetailController(invoiceId: string) {
  const [manualDialogOpen, setManualDialogOpen] = useState(false);
  const detail = useInvoiceDetail(invoiceId);
  const initiateOnlinePayment = useInitiateOnlinePayment(invoiceId);
  const cancelInvoice = useCancelInvoice(invoiceId);

  const invoice = detail.data?.invoice;
  const remaining = invoice === undefined ? 0 : invoice.amount - invoice.amountPaid;
  const isPayable = invoice !== undefined && PAYABLE_STATUSES.includes(invoice.status);
  // Backend chỉ cho huỷ khi còn UNPAID (đã có tiền vào thì phải hoàn tiền ngoài hệ thống trước).
  const canCancel = invoice?.status === INVOICE_STATUS.unpaid;

  const openManualDialog = useCallback(() => setManualDialogOpen(true), []);
  const closeManualDialog = useCallback(() => setManualDialogOpen(false), []);

  const onPayOnline = useCallback(
    (gateway: OnlineGateway) => {
      void initiateOnlinePayment.mutateAsync({ gateway }).then((result) => {
        // Mở tab mới thay vì điều hướng cả SPA: kế toán vẫn giữ trang hoá đơn đang mở.
        window.open(result.payUrl, "_blank", "noopener,noreferrer");
      });
    },
    [initiateOnlinePayment],
  );

  const onCancel = useCallback(() => {
    void cancelInvoice.mutateAsync();
  }, [cancelInvoice]);

  return {
    detail,
    remaining,
    isPayable,
    canCancel,
    manualDialogOpen,
    openManualDialog,
    closeManualDialog,
    onPayOnline,
    isPayingOnline: initiateOnlinePayment.isPending,
    onCancel,
    isCancelling: cancelInvoice.isPending,
  };
}
