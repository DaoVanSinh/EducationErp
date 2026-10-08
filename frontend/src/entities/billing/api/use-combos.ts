import { billingApi } from "@/entities/billing/api/billing-api";
import { billingKeys } from "@/entities/billing/api/billing-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useCombos(page: number, size: number, studentProfileId?: string) {
  return useQuery({
    queryKey: billingKeys.comboList(page, size, studentProfileId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => billingApi.listCombos(page, size, studentProfileId),
  });
}
