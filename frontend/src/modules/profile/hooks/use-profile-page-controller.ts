import { useState } from "react";

export type ProfileTab = "profile" | "security" | "payslips" | "contracts" | "permissions";

/** Toàn bộ state của trang Hồ sơ của tôi - ui/* chỉ render (Mandate #2). */
export function useProfilePageController() {
  const [activeTab, setActiveTab] = useState<ProfileTab>("profile");
  return { activeTab, setActiveTab };
}
