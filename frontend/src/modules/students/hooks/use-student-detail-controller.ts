import { useStudent } from "@/entities/student";
import { useCallback, useState } from "react";

export type StudentDetailTab = "profile" | "enrollments" | "invoices" | "combos" | "academic";

/** Toàn bộ state của trang chi tiết học viên - ui/* chỉ render (Mandate #2). Ghi danh/hoá đơn/combo/
 * kết quả học tập fetch ngay trong component tab của chúng (chỉ mount sau khi `student.data` đã
 * có), nên không cần truyền id còn undefined qua một hook top-level. */
export function useStudentDetailController(profileId: string) {
  const [activeTab, setActiveTab] = useState<StudentDetailTab>("profile");
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const student = useStudent(profileId);

  const openEdit = useCallback(() => setEditDialogOpen(true), []);
  const closeEdit = useCallback(() => setEditDialogOpen(false), []);

  return { student, activeTab, setActiveTab, editDialogOpen, openEdit, closeEdit };
}
