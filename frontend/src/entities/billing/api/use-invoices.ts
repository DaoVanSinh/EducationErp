import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useInvoices(
  page: number,
  size: number,
  enrollmentId?: string,
  studentProfileId?: string,
  status?: string,
) {
  return useQuery({
    queryKey: billingKeys.list(page, size, enrollmentId, studentProfileId, status),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => billingApi.listInvoices(page, size, enrollmentId, studentProfileId, status),
  });
}
