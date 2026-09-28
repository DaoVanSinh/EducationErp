import {
  accountSummarySchema,
  type ChangePasswordPayload,
  type ProfileUpdatePayload,
  sessionSchema,
} from "@/entities/account/model/account-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const accountPageSchema = pageResponseSchema(accountSummarySchema);

/**
 * Mọi lời gọi API của entity account. Kết quả đi qua Zod trước khi vào cache: dữ liệu lệch hợp đồng
 * thì lỗi hiện ra tại đúng chỗ gọi, kèm tên trường, thay vì thành lỗi render ở một component xa lắc.
 */
export const accountApi = {
  async getSession(signal?: AbortSignal) {
    return sessionSchema.parse(await apiClient.get<unknown>(API_ROUTE.account.me, undefined, signal));
  },

  async listAccounts(page: number, size: number, signal?: AbortSignal) {
    return accountPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.rbac.accounts, { page, size }, signal),
    );
  },

  async updateProfile(payload: ProfileUpdatePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.account.profile, payload);
  },

  async changePassword(payload: ChangePasswordPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.changePassword, payload);
  },
} as const;
