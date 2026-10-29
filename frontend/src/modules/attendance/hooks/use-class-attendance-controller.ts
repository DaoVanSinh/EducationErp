import { useClassAttendance } from "@/entities/attendance";
import { useCallback, useState } from "react";

/** State của trang Điểm danh theo lớp - ui/* chỉ render (Mandate #2). */
export function useClassAttendanceController(classId: string) {
  const attendance = useClassAttendance(classId);
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  const openDialog = useCallback(() => setIsDialogOpen(true), []);
  const closeDialog = useCallback(() => setIsDialogOpen(false), []);

  return { attendance, isDialogOpen, openDialog, closeDialog };
}
