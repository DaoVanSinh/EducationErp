import { invoiceDetailSchema } from "@/entities/billing";
import { myLearningSchema } from "@/modules/learning/model/learning-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const myInvoicesSchema = z.array(invoiceDetailSchema);

/** Cổng học tập - backend tự lọc theo học viên đang đăng nhập, không gửi id nào lên. */
export const learningApi = {
  async listMyClasses() {
    return myLearningSchema.parse(await apiClient.get<unknown>(API_ROUTE.academic.myClasses));
  },

  async listMyInvoices() {
    return myInvoicesSchema.parse(await apiClient.get<unknown>(API_ROUTE.billing.myInvoices));
  },
} as const;
