import { z } from "zod";

export const roleHeadcountSchema = z.object({
  roleCode: z.string(),
  roleName: z.string(),
  accountCount: z.number(),
});

export type RoleHeadcount = z.infer<typeof roleHeadcountSchema>;

export const recentLoginSchema = z.object({
  accountId: z.string().uuid(),
  email: z.string(),
  fullName: z.string(),
  occurredAt: z.string(),
});

export type RecentLogin = z.infer<typeof recentLoginSchema>;

export const dashboardStatsSchema = z.object({
  totalAccounts: z.number(),
  activeAccounts: z.number(),
  disabledAccounts: z.number(),
  branchCount: z.number(),
  accountsByRole: z.array(roleHeadcountSchema),
  recentLogins: z.array(recentLoginSchema),
});

export type DashboardStats = z.infer<typeof dashboardStatsSchema>;

/**
 * Bốn con số ở đầu trang: nhãn và cách lấy giá trị nằm cạnh nhau để thêm/bớt một ô chỉ sửa một chỗ,
 * còn phần vẽ thì lặp qua danh sách này.
 */
export const DASHBOARD_STAT_CARDS: readonly {
  readonly key: "totalAccounts" | "activeAccounts" | "disabledAccounts" | "branchCount";
  readonly label: string;
  readonly hint: string;
}[] = [
  { key: "totalAccounts", label: "Tổng tài khoản", hint: "Toàn bộ tài khoản trong hệ thống" },
  { key: "activeAccounts", label: "Đang hoạt động", hint: "Đăng nhập được ngay" },
  { key: "disabledAccounts", label: "Đã vô hiệu hoá", hint: "Bị chặn đăng nhập" },
  { key: "branchCount", label: "Chi nhánh", hint: "Số cơ sở đang quản lý" },
];
