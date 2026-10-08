/**
 * Khoá cache của entity branch. Luôn tạo qua factory này: invalidate theo `branchKeys.all` sẽ quét
 * đúng mọi truy vấn con, còn nếu mỗi nơi tự viết mảng khoá thì sẽ có chỗ không bao giờ được làm mới.
 */
export const branchKeys = {
  all: ["branch"] as const,
  lists: () => [...branchKeys.all, "list"] as const,
  list: (page: number, size: number) => [...branchKeys.lists(), { page, size }] as const,
} as const;
