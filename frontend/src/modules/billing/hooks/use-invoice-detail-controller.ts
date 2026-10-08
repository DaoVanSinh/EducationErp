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
      // Final review I5: window.open sau khi await mutateAsync chạy NGOÀI user-activation của cú
      // click, nên Safari/Firefox (và thường cả Chrome) âm thầm chặn popup. Mở tab rỗng NGAY trong
      // handler để giữ user-activation, rồi điều hướng tab đó sang payUrl khi mutation xong. Không
      // dùng "noopener" ở đây vì flag đó khiến window.open trả về null - mất luôn tham chiếu để điều
      // hướng sau; tự đặt opener=null để đạt hiệu quả cô lập tương đương mà vẫn giữ được handle.
      const paymentTab = window.open("", "_blank");
      if (paymentTab) {
        paymentTab.opener = null;
      }
      void initiateOnlinePayment.mutateAsync({ gateway }).then(
        (result) => {
          if (paymentTab) {
            paymentTab.location.href = result.payUrl;
          } else {
            window.open(result.payUrl, "_blank", "noopener,noreferrer");
          }
        },
        () => {
          paymentTab?.close();
        },
      );
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
    onlinePaymentError: initiateOnlinePayment.error,
    onCancel,
    isCancelling: cancelInvoice.isPending,
  };
}
