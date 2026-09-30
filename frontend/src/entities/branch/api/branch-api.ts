import {
  branchSummarySchema,
  type CreateBranchPayload,
  type UpdateBranchPayload,
} from "@/entities/branch/model/branch-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const branchPageSchema = pageResponseSchema(branchSummarySchema);
/** Endpoint tạo chi nhánh trả về thân là chính id vừa tạo, một chuỗi UUID trần không bọc object. */
const createdBranchIdSchema = z.string().uuid();

/**
 * Mọi lời gọi API của entity branch. Kết quả đi qua Zod trước khi vào cache: dữ liệu lệch hợp đồng
 * thì lỗi hiện ra tại đúng chỗ gọi, kèm tên trường, thay vì thành lỗi render ở một component xa lắc.
 */
export const branchApi = {
  async listBranches(page: number, size: number) {
    return branchPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.organization.branches, { page, size }),
    );
  },

  async createBranch(payload: CreateBranchPayload): Promise<string> {
    return createdBranchIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.organization.branches, payload),
    );
  },

  async updateBranch(branchId: string, payload: UpdateBranchPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.organization.branch(branchId), payload);
  },
} as const;
