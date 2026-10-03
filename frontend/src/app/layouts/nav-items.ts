import type { PermissionRequirement } from "@/entities/permission";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";

export interface NavItem {
  readonly path: string;
  readonly label: string;
  /** Không có yêu cầu nghĩa là ai đăng nhập cũng thấy (trang hồ sơ của chính mình). */
  readonly requirement?: PermissionRequirement;
}

export interface NavSection {
  readonly title: string;
  readonly items: readonly NavItem[];
}

export const NAV_SECTIONS: readonly NavSection[] = [
  {
    title: "Điều hành & Nghiệp vụ",
    items: [
      { path: APP_ROUTE.dashboard, label: "Tổng quan", requirement: ACCESS_RULE.readDashboard },
      { path: APP_ROUTE.accounts, label: "Tài khoản", requirement: ACCESS_RULE.readAccount },
      { path: APP_ROUTE.branches, label: "Chi nhánh", requirement: ACCESS_RULE.readBranch },
      { path: APP_ROUTE.courses, label: "Khóa học", requirement: ACCESS_RULE.readCourse },
      { path: APP_ROUTE.classes, label: "Lớp học", requirement: ACCESS_RULE.readClass },
      { path: APP_ROUTE.teachers, label: "Giáo viên", requirement: ACCESS_RULE.readTeacher },
      { path: APP_ROUTE.students, label: "Học viên", requirement: ACCESS_RULE.readStudent },
      { path: APP_ROUTE.payrollContracts, label: "Hợp đồng lao động", requirement: ACCESS_RULE.readPayroll },
      { path: APP_ROUTE.payrollRuns, label: "Kỳ lương", requirement: ACCESS_RULE.readPayroll },
    ],
  },
  {
    title: "Bảo mật & Phân quyền",
    items: [
      { path: APP_ROUTE.roles, label: "Vai trò và quyền", requirement: ACCESS_RULE.readRole },
    ],
  },
  {
    title: "Cá nhân",
    items: [
      { path: APP_ROUTE.profile, label: "Hồ sơ của tôi" },
    ],
  },
];

/** Giữ tương thích ngược với mọi nơi dùng NAV_ITEMS */
export const NAV_ITEMS: readonly NavItem[] = NAV_SECTIONS.flatMap((section) => section.items);
