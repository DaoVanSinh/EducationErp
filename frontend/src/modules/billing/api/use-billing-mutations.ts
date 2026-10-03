import {
  billingApi,
  billingKeys,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type RecordManualPaymentPayload,
} from "@/entities/billing";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useInvoicesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: billingKeys.all });
  };
}

export function useCreateInvoice() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateInvoicePayload) => billingApi.createInvoice(payload),
    onSuccess: invalidate,
  });
}

/** Không invalidate: lúc này chưa có tiền nào vào, hoá đơn chưa đổi trạng thái (backend chỉ lưu một
 * Payment PENDING). Trạng thái thật đến từ IPN, trang chi tiết sẽ thấy khi người dùng quay lại. */
export function useInitiateOnlinePayment(invoiceId: string) {
  return useMutation({
    mutationFn: (payload: InitiateOnlinePaymentPayload) =>
      billingApi.initiateOnlinePayment(invoiceId, payload),
  });
}

export function useRecordManualPayment(invoiceId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: RecordManualPaymentPayload) => billingApi.recordManualPayment(invoiceId, payload),
    onSuccess: invalidate,
  });
}

export function useCancelInvoice(invoiceId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: () => billingApi.cancelInvoice(invoiceId),
    onSuccess: invalidate,
  });
}
