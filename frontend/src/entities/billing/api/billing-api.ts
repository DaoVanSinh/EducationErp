import {
  comboDetailSchema,
  comboDiscountTierSchema,
  comboSchema,
  invoiceDetailSchema,
  invoiceSummarySchema,
  paymentSchema,
  payUrlSchema,
  type CreateComboDiscountTierPayload,
  type CreateComboInvoicePayload,
  type CreateComboPayload,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type RecordManualPaymentPayload,
  type UpdateComboDiscountTierPayload,
} from "@/entities/billing/model/billing-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const invoicePageSchema = pageResponseSchema(invoiceSummarySchema);
const comboPageSchema = pageResponseSchema(comboSchema);
const comboDiscountTierListSchema = z.array(comboDiscountTierSchema);

export const billingApi = {
  async listInvoices(
    page: number,
    size: number,
    enrollmentId?: string,
    studentProfileId?: string,
    status?: string,
  ) {
    return invoicePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.invoices, {
        page,
        size,
        enrollmentId,
        studentProfileId,
        status,
      }),
    );
  },

  async getInvoice(invoiceId: string) {
    return invoiceDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.billing.invoice(invoiceId)));
  },

  async createInvoice(payload: CreateInvoicePayload) {
    return invoiceSummarySchema.parse(await apiClient.post<unknown>(API_ROUTE.billing.invoices, payload));
  },

  async initiateOnlinePayment(invoiceId: string, payload: InitiateOnlinePaymentPayload) {
    return payUrlSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.invoiceOnlinePayment(invoiceId), payload),
    );
  },

  async recordManualPayment(invoiceId: string, payload: RecordManualPaymentPayload) {
    return invoiceSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.invoiceManualPayment(invoiceId), payload),
    );
  },

  async cancelInvoice(invoiceId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.billing.invoiceCancel(invoiceId));
  },

  async listCombos(page: number, size: number, studentProfileId?: string) {
    return comboPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.combos, { page, size, studentProfileId }),
    );
  },

  async getCombo(comboId: string) {
    return comboDetailSchema.parse(await apiClient.get<unknown>(API_ROUTE.billing.combo(comboId)));
  },

  async createCombo(payload: CreateComboPayload) {
    return comboSchema.parse(await apiClient.post<unknown>(API_ROUTE.billing.combos, payload));
  },

  async cancelCombo(comboId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.billing.comboCancel(comboId));
  },

  /** Trả về một InvoiceSummary như createInvoice - hoá đơn combo và hoá đơn đơn-khoá cùng một kiểu. */
  async createComboInvoice(payload: CreateComboInvoicePayload) {
    return invoiceSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.comboInvoices, payload),
    );
  },

  /** Không phân trang: backend trả thẳng một mảng (số bậc là con số nhỏ do admin tự nhập). */
  async listComboDiscountTiers() {
    return comboDiscountTierListSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.comboDiscountTiers),
    );
  },

  async createComboDiscountTier(payload: CreateComboDiscountTierPayload) {
    return comboDiscountTierSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.billing.comboDiscountTiers, payload),
    );
  },

  async updateComboDiscountTier(tierId: string, payload: UpdateComboDiscountTierPayload) {
    return comboDiscountTierSchema.parse(
      await apiClient.patch<unknown>(API_ROUTE.billing.comboDiscountTier(tierId), payload),
    );
  },

  /** Endpoint public - gọi được khi chưa đăng nhập (trang Return URL). */
  async getPaymentStatus(gatewayTransactionId: string) {
    return paymentSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.paymentStatus(gatewayTransactionId)),
    );
  },
} as const;
