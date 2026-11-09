import { usePermissions } from "@/entities/permission";
import { useTeacher } from "@/entities/teacher";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useCallback, useState } from "react";

export type TeacherDetailTab = "profile" | "classes" | "pay";

/** Toàn bộ state của trang chi tiết giáo viên - ui/* chỉ render (Mandate #2). Lớp đang dạy và lương fetch ngay
 * trong component tab của chúng (chỉ mount sau khi `teacher.data` đã có). Tab Lương chỉ hiện với người có quyền
 * xem lương: quyền đọc giáo viên không kéo theo quyền xem mức lương. */
export function useTeacherDetailController(profileId: string) {
  const permissions = usePermissions();
  const [activeTab, setActiveTab] = useState<TeacherDetailTab>("profile");
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [leaveDialogOpen, setLeaveDialogOpen] = useState(false);
  const teacher = useTeacher(profileId);
  const canReadPay = permissions.allows(ACCESS_RULE.readPayroll);

  const openEdit = useCallback(() => setEditDialogOpen(true), []);
  const closeEdit = useCallback(() => setEditDialogOpen(false), []);
  const openLeave = useCallback(() => setLeaveDialogOpen(true), []);
  const closeLeave = useCallback(() => setLeaveDialogOpen(false), []);
  const isTabVisible = useCallback((tab: TeacherDetailTab) => tab !== "pay" || canReadPay, [canReadPay]);

  return {
    teacher,
    activeTab,
    setActiveTab,
    editDialogOpen,
    openEdit,
    closeEdit,
    leaveDialogOpen,
    openLeave,
    closeLeave,
    isTabVisible,
  };
}
