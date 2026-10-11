import { useTeacher } from "@/entities/teacher";
import { useCallback, useState } from "react";

export type TeacherDetailTab = "profile" | "classes" | "contracts";

/** Toàn bộ state của trang chi tiết giáo viên - ui/* chỉ render (Mandate #2). Lớp đang dạy/hợp đồng
 * lao động fetch ngay trong component tab của chúng (chỉ mount sau khi `teacher.data` đã có), nên
 * không cần truyền `accountId` còn undefined qua một hook top-level. */
export function useTeacherDetailController(profileId: string) {
  const [activeTab, setActiveTab] = useState<TeacherDetailTab>("profile");
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const teacher = useTeacher(profileId);

  const openEdit = useCallback(() => setEditDialogOpen(true), []);
  const closeEdit = useCallback(() => setEditDialogOpen(false), []);

  return { teacher, activeTab, setActiveTab, editDialogOpen, openEdit, closeEdit };
}
