import { billingApi, PAYMENT_METHOD } from "@/entities/billing";
import { useMutation } from "@tanstack/react-query";

/** Học viên tạo link VNPay cho phần còn lại của hoá đơn của chính mình; trả payUrl để chuyển trang. */
export function usePayMyInvoice() {
  return useMutation({
    mutationFn: (invoiceId: string) =>
      billingApi.initiateMyOnlinePayment(invoiceId, { gateway: PAYMENT_METHOD.vnpay }),
  });
}
