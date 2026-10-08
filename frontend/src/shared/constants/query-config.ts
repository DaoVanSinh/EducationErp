/**
 * Nhịp làm mới dữ liệu. Quyền và phiên phải mới: sau khi admin đổi nhóm quyền, backend đã xoá cache
 * Redis nên lần gọi sau đã đúng - việc còn lại của frontend là đừng giữ bản cũ quá lâu.
 */
export const QUERY_STALE_TIME_MS = {
  session: 30_000,
  /** Dữ liệu tham chiếu (vai trò, chi nhánh, nhóm quyền) gần như không đổi trong một lần làm việc. */
  catalog: 5 * 60_000,
  list: 15_000,
  dashboard: 60_000,
} as const;

export const DEFAULT_PAGE_SIZE = 20;

/** Số lần thử lại một truy vấn khi lỗi. Lỗi 4xx thì thử lại chỉ làm chậm phản hồi cho người dùng. */
export const QUERY_RETRY_COUNT = 1;
