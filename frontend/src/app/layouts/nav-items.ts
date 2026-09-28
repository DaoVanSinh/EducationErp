import type { PermissionRequirement } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";

export interface NavItem {
  readonly path: string;
  readonly label: string;
  /** Không có yêu cầu nghĩa là ai đăng nhập cũng thấy (trang hồ sơ của chính mình). */
  readonly requirement?: PermissionRequirement;
}

/**
 * Thanh điều hướng lọc theo quyền, dùng đúng yêu cầu mà backend kiểm tra - để không có mục nào dẫn
 * người dùng tới một trang chỉ để đọc "bạn không có quyền".
 */
export const NAV_ITEMS: readonly NavItem[] = [
  { path: APP_ROUTE.dashboard, label: "Tổng quan", requirement: ACCESS_RULE.readDashboard },
  { path: APP_ROUTE.accounts, label: "Tài khoản", requirement: ACCESS_RULE.readAccount },
  { path: APP_ROUTE.roles, label: "Vai trò và quyền", requirement: ACCESS_RULE.readRole },
  { path: APP_ROUTE.profile, label: "Hồ sơ của tôi" },
];
