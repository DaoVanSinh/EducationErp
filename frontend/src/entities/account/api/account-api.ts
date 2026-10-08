import {
  accountSummarySchema,
  type ChangePasswordPayload,
  type CreateAccountPayload,
  type ProfileUpdatePayload,
  sessionSchema,
} from "@/entities/account/model/account-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const accountPageSchema = pageResponseSchema(accountSummarySchema);

/**
 * Mọi lời gọi API của entity account. Kết quả đi qua Zod trước khi vào cache: dữ liệu lệch hợp đồng
 * thì lỗi hiện ra tại đúng chỗ gọi, kèm tên trường, thay vì thành lỗi render ở một component xa lắc.
 */
export const accountApi = {
  async getSession() {
    return sessionSchema.parse(await apiClient.get<unknown>(API_ROUTE.account.me));
  },

  async listAccounts(page: number, size: number, branchId: string | null) {
    return accountPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.rbac.accounts, { page, size, branchId: branchId ?? undefined }),
    );
  },

  async updateProfile(payload: ProfileUpdatePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.account.profile, payload);
  },

  async changePassword(payload: ChangePasswordPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.changePassword, payload);
  },

  async createAccount(payload: CreateAccountPayload): Promise<string> {
    return z.string().uuid().parse(await apiClient.post<unknown>(API_ROUTE.rbac.accounts, payload));
  },

  async resendInvite(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountResendInvite(accountId));
  },

  async revokeInvite(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountRevokeInvite(accountId));
  },
} as const;
