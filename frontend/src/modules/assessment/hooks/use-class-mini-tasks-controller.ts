import { useClassMiniTasks, type MiniTask } from "@/entities/assessment";
import { useCallback, useState } from "react";

/** "new" = đang soạn mini-task mới, một MiniTask = đang sửa nó. */
export type MiniTaskFormTarget = MiniTask | "new";

/** State của trang Mini-task theo lớp (Mandate #2). */
export function useClassMiniTasksController(classId: string) {
  const board = useClassMiniTasks(classId);
  const [formTarget, setFormTarget] = useState<MiniTaskFormTarget | null>(null);

  const openCreate = useCallback(() => setFormTarget("new"), []);
  const openEdit = useCallback((task: MiniTask) => setFormTarget(task), []);
  const closeForm = useCallback(() => setFormTarget(null), []);

  return { board, formTarget, openCreate, openEdit, closeForm };
}
