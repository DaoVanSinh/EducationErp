import { usePermissions } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useCallback, useState } from "react";

export type ProfileTab = "profile" | "security" | "payslips" | "contracts" | "permissions";

/** Hai tab chỉ có nghĩa với nhân sự (người có hợp đồng lao động/phiếu lương). */
const STAFF_ONLY_TABS: ReadonlySet<ProfileTab> = new Set<ProfileTab>(["payslips", "contracts"]);

/**
 * Toàn bộ state của trang Hồ sơ của tôi - ui/* chỉ render (Mandate #2).
 *
 * Tab Lương/Hợp đồng chỉ hiện với nhân sự: giáo viên (cổng giảng dạy) hoặc người quản trị lương.
 * Học viên không bao giờ có hợp đồng lao động, hiện hai tab rỗng đó chỉ gây hiểu nhầm.
 */
export function useProfilePageController() {
  const permissions = usePermissions();
  const [activeTab, setActiveTab] = useState<ProfileTab>("profile");
  const isStaff = permissions.allows(ACCESS_RULE.readTeaching) || permissions.allows(ACCESS_RULE.readPayroll);

  const isTabVisible = useCallback((tab: ProfileTab) => isStaff || !STAFF_ONLY_TABS.has(tab), [isStaff]);

  return { activeTab, setActiveTab, isTabVisible };
}
