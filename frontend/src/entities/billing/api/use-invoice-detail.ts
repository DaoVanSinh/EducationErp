import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function useInvoiceDetail(invoiceId: string) {
  return useQuery({
    queryKey: billingKeys.detail(invoiceId),
    retry: QUERY_RETRY_COUNT,
    queryFn: () => billingApi.getInvoice(invoiceId),
  });
}
