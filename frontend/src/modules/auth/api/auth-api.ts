import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

export interface LoginPayload {
  readonly email: string;
  readonly password: string;
}

export interface CompleteInvitePayload {
  readonly email: string;
  readonly currentPassword: string;
  readonly newPassword: string;
}

export interface ResetPasswordPayload {
  readonly token: string;
  readonly newPassword: string;
}

const loginResponseSchema = z.object({ requiresPasswordChange: z.boolean() });

/**
 * Các endpoint vòng đời phiên. Không endpoint nào trả token về cho JavaScript: access/refresh token đi
 * bằng cookie HttpOnly, nên ở đây chỉ có "gọi và xem có lỗi không".
 */
export const authApi = {
  async login(payload: LoginPayload): Promise<{ requiresPasswordChange: boolean }> {
    return loginResponseSchema.parse(await apiClient.post<unknown>(API_ROUTE.auth.login, payload));
  },

  async completeInvite(payload: CompleteInvitePayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.auth.completeInvite, payload);
  },

  async logout(): Promise<void> {
    await apiClient.post<void>(API_ROUTE.auth.logout);
  },

  async forgotPassword(email: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.forgotPassword, { email });
  },

  async resetPassword(payload: ResetPasswordPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.account.resetPassword, payload);
  },
} as const;
