import {
  invoiceDetailSchema,
  invoiceSummarySchema,
  paymentSchema,
  payUrlSchema,
  type CreateInvoicePayload,
  type InitiateOnlinePaymentPayload,
  type RecordManualPaymentPayload,
} from "@/entities/billing/model/billing-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const invoicePageSchema = pageResponseSchema(invoiceSummarySchema);

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

  /** Endpoint public - gọi được khi chưa đăng nhập (trang Return URL). */
  async getPaymentStatus(gatewayTransactionId: string) {
    return paymentSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.billing.paymentStatus(gatewayTransactionId)),
    );
  },
} as const;
