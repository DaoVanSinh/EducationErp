import type { ClassListFilter } from "@/entities/class/api/class-api";

export const classKeys = {
  all: ["class"] as const,
  lists: () => [...classKeys.all, "list"] as const,
  list: (page: number, size: number, filter: ClassListFilter = {}) =>
    [...classKeys.lists(), { page, size, ...filter }] as const,
  /** Lớp của giáo viên đang đăng nhập - nằm dưới `lists()` để mọi mutation lớp học đã invalidate
   * `lists()` cũng làm mới luôn trang "Lớp của tôi". */
  mine: (page: number, size: number) => [...classKeys.lists(), "mine", { page, size }] as const,
} as const;
