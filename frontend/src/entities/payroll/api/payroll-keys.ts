export const payrollKeys = {
  contracts: {
    all: ["payroll", "contract"] as const,
    lists: () => [...payrollKeys.contracts.all, "list"] as const,
    list: (page: number, size: number, accountId?: string) =>
      [...payrollKeys.contracts.lists(), { page, size, accountId }] as const,
    myList: (page: number, size: number) => [...payrollKeys.contracts.all, "my-list", { page, size }] as const,
  },
  payslips: {
    all: ["payroll", "payslip"] as const,
    myList: (page: number, size: number) => [...payrollKeys.payslips.all, "my-list", { page, size }] as const,
  },
  runs: {
    all: ["payroll", "run"] as const,
    lists: () => [...payrollKeys.runs.all, "list"] as const,
    list: (page: number, size: number) => [...payrollKeys.runs.lists(), { page, size }] as const,
    detail: (runId: string) => [...payrollKeys.runs.all, "detail", runId] as const,
  },
} as const;
