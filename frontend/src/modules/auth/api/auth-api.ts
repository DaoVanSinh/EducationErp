import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export interface LoginPayload {
  readonly email: string;
  readonly password: string;
}

export interface ResetPasswordPayload {
  readonly token: string;
  readonly newPassword: string;
}

/**
 * Các endpoint vòng đời phiên. Không endpoint nào trả token về cho JavaScript: access/refresh token đi
 * bằng cookie HttpOnly, nên ở đây chỉ có "gọi và xem có lỗi không".
 */
export const authApi = {
  async login(payload: LoginPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.auth.login, payload);
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
