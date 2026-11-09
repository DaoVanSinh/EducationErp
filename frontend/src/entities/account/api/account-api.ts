import {
  accountSummarySchema,
  avatarUploadResponseSchema,
  type AccountListFilters,
  type AvatarUploadResponse,
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

function blankToUndefined(value: string): string | undefined {
  return value.length === 0 ? undefined : value;
}

/**
 * Mọi lời gọi API của entity account. Kết quả đi qua Zod trước khi vào cache: dữ liệu lệch hợp đồng
 * thì lỗi hiện ra tại đúng chỗ gọi, kèm tên trường, thay vì thành lỗi render ở một component xa lắc.
 */
export const accountApi = {
  async getSession() {
    return sessionSchema.parse(await apiClient.get<unknown>(API_ROUTE.account.me));
  },

  async listAccounts(page: number, size: number, filters: AccountListFilters) {
    return accountPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.rbac.accounts, {
        page,
        size,
        branchId: filters.branchId ?? undefined,
        roleCode: blankToUndefined(filters.roleCode),
        status: blankToUndefined(filters.status),
        query: blankToUndefined(filters.query),
      }),
    );
  },

  async updateProfile(payload: ProfileUpdatePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.account.profile, payload);
  },

  async saveAvatar(image: File | null): Promise<AvatarUploadResponse> {
    const formData = new FormData();
    if (image) {
      formData.append("image", image);
    } else {
      formData.append("remove", "true");
    }
    return avatarUploadResponseSchema.parse(
      await apiClient.postForm<unknown>(API_ROUTE.account.avatar, formData),
    );
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

  /** Đổi vai trò của tài khoản đã có - backend chặn khi còn dạy lớp đang mở / còn đang học. */
  async changeRole(accountId: string, roleId: string): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.rbac.accountRole(accountId), { roleId });
  },

  /** Tạm đóng: không đăng nhập được, mọi phiên đang mở bị thu hồi ngay, mở lại được. */
  async disable(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountDisable(accountId));
  },

  async enable(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountEnable(accountId));
  },

  /** Đóng vĩnh viễn: email được giải phóng để tạo tài khoản mới, dữ liệu cũ giữ nguyên. */
  async close(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountClose(accountId));
  },

  /** Đăng xuất khỏi mọi thiết bị, tài khoản vẫn hoạt động. */
  async signOutEverywhere(accountId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.rbac.accountSignOutEverywhere(accountId));
  },
} as const;
