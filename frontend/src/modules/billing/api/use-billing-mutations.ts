import {
  billingApi,
  billingKeys,
  type CreateComboInvoicePayload,
  type CreateComboPayload,
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

export function useCreateCombo() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboPayload) => billingApi.createCombo(payload),
    onSuccess: invalidate,
  });
}

/** Huỷ combo xoá cứng bản ghi, nên phải dọn CẢ cache danh sách lẫn cache chi tiết - invalidate
 * billingKeys.all làm cả hai trong một lần. */
export function useCancelCombo(comboId: string) {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: () => billingApi.cancelCombo(comboId),
    onSuccess: invalidate,
  });
}

export function useCreateComboInvoice() {
  const invalidate = useInvoicesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateComboInvoicePayload) => billingApi.createComboInvoice(payload),
    onSuccess: invalidate,
  });
}
