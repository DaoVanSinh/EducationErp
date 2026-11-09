import { usePermissions } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useCallback, useState } from "react";

export type ProfileTab = "profile" | "security" | "payslips" | "permissions";

/**
 * Toàn bộ state của trang Hồ sơ của tôi - ui/* chỉ render (Mandate #2).
 *
 * Tab Lương chỉ hiện với giáo viên (cổng giảng dạy) hoặc người quản trị lương. Học viên không bao giờ có phiếu
 * lương, hiện tab rỗng đó chỉ gây hiểu nhầm.
 */
export function useProfilePageController() {
  const permissions = usePermissions();
  const [activeTab, setActiveTab] = useState<ProfileTab>("profile");
  const isStaff = permissions.allows(ACCESS_RULE.readTeaching) || permissions.allows(ACCESS_RULE.readPayroll);

  const isTabVisible = useCallback((tab: ProfileTab) => isStaff || tab !== "payslips", [isStaff]);

  return { activeTab, setActiveTab, isTabVisible };
}
