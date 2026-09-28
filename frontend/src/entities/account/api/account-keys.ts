/**
 * Khoá cache của entity account. Luôn tạo qua factory này: invalidate theo `accountKeys.all` sẽ quét
 * đúng mọi truy vấn con, còn nếu mỗi nơi tự viết mảng khoá thì sẽ có chỗ không bao giờ được làm mới.
 */
export const accountKeys = {
  all: ["account"] as const,
  session: () => [...accountKeys.all, "session"] as const,
  lists: () => [...accountKeys.all, "list"] as const,
  list: (page: number, size: number) => [...accountKeys.lists(), { page, size }] as const,
} as const;
