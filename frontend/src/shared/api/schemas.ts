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

/**
 * Một cột điểm đã lưu của khóa học, khớp `courses.dto.GradeColumnResponse` ở backend.
 *
 * Đặt ở shared cùng lý do với {@link grantedPermissionSchema}: cả entity course (màn hình sửa khóa
 * học cấu hình cột điểm) lẫn entity academic (sổ điểm hiển thị cột điểm) đều cần, mà hai entity thì
 * không được import nhau. `weightPercent`/`maxScore` là số (backend trả NUMERIC(5,2) dưới dạng số
 * JSON, không phải chuỗi).
 */
export const gradeColumnSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  weightPercent: z.number(),
  maxScore: z.number(),
  displayOrder: z.number().int(),
});

export type GradeColumn = z.infer<typeof gradeColumnSchema>;

/**
 * Một dòng cột điểm khi GỬI LÊN, khớp `courses.dto.GradeColumnSlot`. Không có `id`: PATCH thay toàn
 * bộ danh sách, không vá từng dòng (mirror `weeklyScheduleSlotSchema` của entity class).
 */
export const gradeColumnSlotSchema = z.object({
  name: z.string(),
  weightPercent: z.number(),
  maxScore: z.number(),
  displayOrder: z.number().int(),
});

export type GradeColumnSlot = z.infer<typeof gradeColumnSlotSchema>;

/** Tổng trọng số bắt buộc, khớp `CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT`. */
export const REQUIRED_TOTAL_WEIGHT_PERCENT = 100;

/** Sai số làm tròn chấp nhận được, khớp `CoursesConstants.GradeColumns.WEIGHT_TOLERANCE`. */
export const WEIGHT_TOLERANCE = 0.01;
