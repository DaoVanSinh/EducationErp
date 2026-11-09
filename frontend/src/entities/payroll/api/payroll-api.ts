import {
  branchPayslipSchema,
  myPayslipSchema,
  payrollRunDetailSchema,
  payrollRunSummarySchema,
  payslipSchema,
  type BranchPayslipFilters,
  type CreatePayrollRunPayload,
  type MarkPayslipPaidPayload,
  type RejectPayrollRunPayload,
  type UndoPayslipPaymentPayload,
  type UpdatePayslipPayload,
} from "@/entities/payroll/model/payroll-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const payrollRunPageSchema = pageResponseSchema(payrollRunSummarySchema);
const myPayslipPageSchema = pageResponseSchema(myPayslipSchema);
const branchPayslipPageSchema = pageResponseSchema(branchPayslipSchema);

export const payslipApi = {
  /** Phiếu lương CỦA CHÍNH người gọi, chỉ từ kỳ đã duyệt - không cần quyền READ_PAYROLL. */
  async listMyPayslips(page: number, size: number) {
    return myPayslipPageSchema.parse(await apiClient.get<unknown>(API_ROUTE.payroll.myPayslips, { page, size }));
  },

  /** Phiếu đã duyệt để chi trả: admin chi nhánh luôn bị backend ép về chi nhánh của mình. */
  async listBranchPayslips(page: number, size: number, filters: BranchPayslipFilters) {
    return branchPayslipPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.payroll.branchPayslips, { page, size, ...filters }),
    );
  },

  async markPaid(payslipId: string, payload: MarkPayslipPaidPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.branchPayslipPay(payslipId), payload);
  },

  /** Chỉ admin gốc huỷ được đánh dấu nhầm, bắt buộc có lý do. */
  async undoPayment(payslipId: string, payload: UndoPayslipPaymentPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.branchPayslipUndo(payslipId), payload);
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

  async recalculate(runId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.payroll.runRecalculate(runId));
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
