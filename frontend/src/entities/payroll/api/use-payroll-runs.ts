import { payrollRunApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function usePayrollRuns(page: number, size: number) {
  return useQuery({
    queryKey: payrollKeys.runs.list(page, size),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => payrollRunApi.listPayrollRuns(page, size),
  });
}
