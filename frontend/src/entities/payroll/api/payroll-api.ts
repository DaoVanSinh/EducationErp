import {
  contractSummarySchema,
  payrollRunDetailSchema,
  payrollRunSummarySchema,
  payslipSchema,
  type CreateContractPayload,
  type CreatePayrollRunPayload,
  type RejectPayrollRunPayload,
  type UpdateContractPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll/model/payroll-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const contractPageSchema = pageResponseSchema(contractSummarySchema);
const payrollRunPageSchema = pageResponseSchema(payrollRunSummarySchema);

function toContractFormData(payload: CreateContractPayload | UpdateContractPayload, file: File | null): FormData {
  const formData = new FormData();
  formData.append("request", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  if (file) {
    formData.append("file", file);
  }
  return formData;
}

export const contractApi = {
  async listContracts(page: number, size: number, accountId?: string) {
    return contractPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.payroll.contracts, { page, size, accountId }),
    );
  },

  async createContract(payload: CreateContractPayload, file: File | null) {
    return contractSummarySchema.parse(
      await apiClient.postForm<unknown>(API_ROUTE.payroll.contracts, toContractFormData(payload, file)),
    );
  },

  async updateContract(contractId: string, payload: UpdateContractPayload, file: File | null) {
    return contractSummarySchema.parse(
      await apiClient.patchForm<unknown>(API_ROUTE.payroll.contract(contractId), toContractFormData(payload, file)),
    );
  },

  async terminateContract(contractId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.contractTerminate(contractId));
  },

  async downloadContractFile(contractId: string): Promise<{ blob: Blob; fileName: string | null }> {
    return apiClient.getBlob(API_ROUTE.payroll.contractFile(contractId));
  },
} as const;

export const payrollRunApi = {
  async listPayrollRuns(page: number, size: number) {
    return payrollRunPageSchema.parse(await apiClient.get<unknown>(API_ROUTE.payroll.runs, { page, size }));
  },

  async createPayrollRun(payload: CreatePayrollRunPayload) {
    return payrollRunSummarySchema.parse(await apiClient.post<unknown>(API_ROUTE.payroll.runs, payload));
  },

  async getPayrollRun(runId: string) {
    return payrollRunDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.payroll.run(runId)));
  },

  async updatePayslip(runId: string, payslipId: string, payload: UpdatePayslipPayload) {
    return payslipSchema.parse(
      await apiClient.patch<unknown>(API_ROUTE.payroll.payslip(runId, payslipId), payload),
    );
  },

  async submitForApproval(runId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runSubmit(runId));
  },

  async approvePayrollRun(runId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runApprove(runId));
  },

  async rejectPayrollRun(runId: string, payload: RejectPayrollRunPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runReject(runId), payload);
  },
} as const;
