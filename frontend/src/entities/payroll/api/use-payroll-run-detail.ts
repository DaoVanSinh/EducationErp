import { payrollRunApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import { QUERY_RETRY_COUNT } from "@/shared/constants/query-config";
import { useQuery } from "@tanstack/react-query";

export function usePayrollRunDetail(runId: string) {
  return useQuery({
    queryKey: payrollKeys.runs.detail(runId),
    retry: QUERY_RETRY_COUNT,
    queryFn: () => payrollRunApi.getPayrollRun(runId),
  });
}
