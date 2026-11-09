import type { BranchPayslipFilters } from "@/entities/payroll/model/payroll-schema";

export const payrollKeys = {
  payslips: {
    all: ["payroll", "payslip"] as const,
    myList: (page: number, size: number) => [...payrollKeys.payslips.all, "my-list", { page, size }] as const,
    branchList: (page: number, size: number, filters: BranchPayslipFilters) =>
      [...payrollKeys.payslips.all, "branch-list", { page, size, ...filters }] as const,
  },
  runs: {
    all: ["payroll", "run"] as const,
    lists: () => [...payrollKeys.runs.all, "list"] as const,
    list: (page: number, size: number) => [...payrollKeys.runs.lists(), { page, size }] as const,
    detail: (runId: string) => [...payrollKeys.runs.all, "detail", runId] as const,
  },
} as const;
