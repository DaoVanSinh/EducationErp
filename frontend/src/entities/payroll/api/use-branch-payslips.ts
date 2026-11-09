import { payslipApi } from "@/entities/payroll/api/payroll-api";
import { payrollKeys } from "@/entities/payroll/api/payroll-keys";
import type { BranchPayslipFilters } from "@/entities/payroll/model/payroll-schema";
import { QUERY_RETRY_COUNT, QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { keepPreviousData, useQuery } from "@tanstack/react-query";

export function useBranchPayslips(page: number, size: number, filters: BranchPayslipFilters) {
  return useQuery({
    queryKey: payrollKeys.payslips.branchList(page, size, filters),
    staleTime: QUERY_STALE_TIME_MS.list,
    retry: QUERY_RETRY_COUNT,
    placeholderData: keepPreviousData,
    queryFn: () => payslipApi.listBranchPayslips(page, size, filters),
  });
}
