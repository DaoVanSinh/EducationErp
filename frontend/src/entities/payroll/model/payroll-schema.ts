import { z } from "zod";

/** Khớp PayrollConstants.ContractType ở backend. */
export const CONTRACT_TYPE = {
  official: "OFFICIAL",
  collaborator: "COLLABORATOR",
} as const;

export type ContractType = (typeof CONTRACT_TYPE)[keyof typeof CONTRACT_TYPE];

export const CONTRACT_TYPE_LABEL: Record<ContractType, string> = {
  [CONTRACT_TYPE.official]: "HĐLĐ chính thức",
  [CONTRACT_TYPE.collaborator]: "CTV / thời vụ",
};

/** Khớp PayrollConstants.ContractStatus ở backend. */
export const CONTRACT_STATUS = {
  active: "ACTIVE",
  terminated: "TERMINATED",
} as const;

export type ContractStatus = (typeof CONTRACT_STATUS)[keyof typeof CONTRACT_STATUS];

export const CONTRACT_STATUS_LABEL: Record<ContractStatus, string> = {
  [CONTRACT_STATUS.active]: "Đang hiệu lực",
  [CONTRACT_STATUS.terminated]: "Đã kết thúc",
};

/** Khớp PayrollConstants.PayrollRunStatus ở backend. */
export const PAYROLL_RUN_STATUS = {
  draft: "DRAFT",
  pendingApproval: "PENDING_APPROVAL",
  approved: "APPROVED",
} as const;

export type PayrollRunStatus = (typeof PAYROLL_RUN_STATUS)[keyof typeof PAYROLL_RUN_STATUS];

export const PAYROLL_RUN_STATUS_LABEL: Record<PayrollRunStatus, string> = {
  [PAYROLL_RUN_STATUS.draft]: "Nháp",
  [PAYROLL_RUN_STATUS.pendingApproval]: "Chờ duyệt",
  [PAYROLL_RUN_STATUS.approved]: "Đã duyệt",
};

export const allowanceSchema = z.object({
  name: z.string(),
  amount: z.number(),
});

export type Allowance = z.infer<typeof allowanceSchema>;

export const contractSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  accountFullName: z.string().nullable(),
  accountEmail: z.string().nullable(),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  status: z.enum([CONTRACT_STATUS.active, CONTRACT_STATUS.terminated]),
  baseSalary: z.number().nullable(),
  hourlyRate: z.number().nullable(),
  probationStartDate: z.string().nullable(),
  probationEndDate: z.string().nullable(),
  startDate: z.string(),
  endDate: z.string().nullable(),
  allowances: z.array(allowanceSchema),
  contractFileKey: z.string().nullable(),
});

export type ContractSummary = z.infer<typeof contractSummarySchema>;

export const payslipSchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  accountFullName: z.string().nullable(),
  contractType: z.enum([CONTRACT_TYPE.official, CONTRACT_TYPE.collaborator]),
  grossPay: z.number(),
  socialInsuranceEmployee: z.number(),
  socialInsuranceEmployer: z.number(),
  incomeTaxWithheld: z.number(),
  netPay: z.number(),
  hoursWorked: z.number().nullable(),
  inProbation: z.boolean(),
});

export type Payslip = z.infer<typeof payslipSchema>;

export const payrollRunSummarySchema = z.object({
  id: z.string().uuid(),
  year: z.number().int(),
  month: z.number().int(),
  status: z.enum([PAYROLL_RUN_STATUS.draft, PAYROLL_RUN_STATUS.pendingApproval, PAYROLL_RUN_STATUS.approved]),
  payslipCount: z.number().int(),
  totalGrossPay: z.number(),
});

export type PayrollRunSummary = z.infer<typeof payrollRunSummarySchema>;

export const payrollRunDetailSchema = z.object({
  run: payrollRunSummarySchema,
  payslips: z.array(payslipSchema),
});

export type PayrollRunDetail = z.infer<typeof payrollRunDetailSchema>;

export interface AllowancePayload {
  readonly name: string;
  readonly amount: number;
}

export interface CreateContractPayload {
  readonly accountId: string;
  readonly contractType: ContractType;
  readonly baseSalary: number | null;
  readonly hourlyRate: number | null;
  readonly probationStartDate: string | null;
  readonly probationEndDate: string | null;
  readonly startDate: string;
  readonly allowances: readonly AllowancePayload[];
}

export interface UpdateContractPayload {
  readonly baseSalary: number | null;
  readonly hourlyRate: number | null;
  readonly probationEndDate: string | null;
  readonly allowances: readonly AllowancePayload[];
}

export interface CreatePayrollRunPayload {
  readonly year: number;
  readonly month: number;
}

export interface UpdatePayslipPayload {
  readonly hoursWorked: number | null;
  readonly incomeTaxWithheld: number | null;
  readonly note: string | null;
}

export interface RejectPayrollRunPayload {
  readonly reason: string;
}
