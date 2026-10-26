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

/**
 * Thứ tự section cũng là thứ tự ưu tiên chọn trang chủ sau đăng nhập (useLandingPath): mục đầu tiên
 * người dùng có quyền là nơi họ được đưa tới - admin vào Tổng quan, giáo viên vào Lớp của tôi, học
 * viên vào Học tập của tôi.
 */
export const NAV_SECTIONS: readonly NavSection[] = [
  {
    title: "Tổng quan",
    items: [
      { path: APP_ROUTE.dashboard, label: "Tổng quan", requirement: ACCESS_RULE.readDashboard },
    ],
  },
  {
    title: "Giảng dạy",
    items: [
      { path: APP_ROUTE.myTeachingClasses, label: "Lớp của tôi", requirement: ACCESS_RULE.readTeaching },
    ],
  },
  {
    title: "Học tập",
    items: [
      { path: APP_ROUTE.myLearning, label: "Học tập của tôi", requirement: ACCESS_RULE.readLearning },
      { path: APP_ROUTE.myInvoices, label: "Học phí của tôi", requirement: ACCESS_RULE.readLearning },
    ],
  },
  {
    title: "Tổ chức",
    items: [
      { path: APP_ROUTE.accounts, label: "Tài khoản", requirement: ACCESS_RULE.readAccount },
      { path: APP_ROUTE.branches, label: "Chi nhánh", requirement: ACCESS_RULE.readBranch },
    ],
  },
  {
    title: "Đào tạo",
    items: [
      { path: APP_ROUTE.courses, label: "Khóa học", requirement: ACCESS_RULE.readCourse },
      { path: APP_ROUTE.classes, label: "Lớp học", requirement: ACCESS_RULE.readClass },
      { path: APP_ROUTE.teachers, label: "Giáo viên", requirement: ACCESS_RULE.readTeacher },
      { path: APP_ROUTE.students, label: "Học viên", requirement: ACCESS_RULE.readStudent },
    ],
  },
  {
    title: "Nhân sự",
    items: [
      { path: APP_ROUTE.payrollContracts, label: "Hợp đồng lao động", requirement: ACCESS_RULE.readPayroll },
      { path: APP_ROUTE.payrollRuns, label: "Kỳ lương", requirement: ACCESS_RULE.readPayroll },
    ],
  },
  {
    title: "Tuyển sinh & Học phí",
    items: [
      { path: APP_ROUTE.enrollments, label: "Ghi danh", requirement: ACCESS_RULE.readEnrollment },
      { path: APP_ROUTE.invoices, label: "Hoá đơn học phí", requirement: ACCESS_RULE.readInvoice },
      { path: APP_ROUTE.combos, label: "Combo khoá học", requirement: ACCESS_RULE.readInvoice },
      {
        path: APP_ROUTE.comboDiscountTiers,
        label: "Bậc giảm giá combo",
        requirement: ACCESS_RULE.readInvoice,
      },
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
