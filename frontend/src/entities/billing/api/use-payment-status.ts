import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { PAYMENT_STATUS } from "@/entities/billing/model/billing-schema";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

/** Khoảng poll khi giao dịch còn PENDING: IPN xử lý bất đồng bộ nên lúc phụ huynh được redirect về,
 * trạng thái thật thường chưa kịp cập nhật (spec mục 10). Dừng poll ngay khi đã có kết quả cuối. */
const PAYMENT_STATUS_POLL_MS = 3_000;

export function usePaymentStatus(gatewayTransactionId: string) {
  return useQuery({
    queryKey: billingKeys.paymentStatus(gatewayTransactionId),
    retry: QUERY_RETRY_COUNT,
    enabled: gatewayTransactionId.length > 0,
    refetchInterval: (query) =>
      query.state.data?.status === PAYMENT_STATUS.pending ? PAYMENT_STATUS_POLL_MS : false,
    queryFn: () => billingApi.getPaymentStatus(gatewayTransactionId),
  });
}
