import { PERMISSION_SCOPE } from "@/shared/constants/permissions";
import { z } from "zod";

/** Cặp id + tên dùng cho mọi dropdown tham chiếu (vai trò, nhóm, chi nhánh, nhóm quyền). */
export const namedReferenceSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
});

export type NamedReference = z.infer<typeof namedReferenceSchema>;

/**
 * Hợp đồng phân trang của backend (PageResponse). Kiểm tra ở biên giúp lỗi lệch hợp đồng hiện ra
 * ngay tại chỗ gọi API, chứ không biến thành "undefined is not a function" giữa lúc render.
 */
export const pageResponseSchema = <TItem extends z.ZodTypeAny>(item: TItem) =>
  z.object({
    items: z.array(item),
    page: z.number().int(),
    size: z.number().int(),
    totalItems: z.number(),
    totalPages: z.number().int(),
  });

export interface PageResponse<TItem> {
  readonly items: readonly TItem[];
  readonly page: number;
  readonly size: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

/**
 * Một quyền đã được cấp, đúng như GET /api/account/me trả về. Đặt ở shared vì cả entity account (đọc
 * phiên) lẫn entity permission (suy luận quyền) đều cần, mà hai entity thì không được import nhau.
 */
export const grantedPermissionSchema = z.object({
  resource: z.string(),
  action: z.string(),
  scope: z.enum([
    PERMISSION_SCOPE.personal,
    PERMISSION_SCOPE.branch,
    PERMISSION_SCOPE.organization,
  ]),
});

export type GrantedPermission = z.infer<typeof grantedPermissionSchema>;
