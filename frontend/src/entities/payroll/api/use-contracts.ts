import { contractApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useContracts(page: number, size: number, accountId?: string) {
  return useQuery({
    queryKey: payrollKeys.contracts.list(page, size, accountId),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => contractApi.listContracts(page, size, accountId),
  });
}
