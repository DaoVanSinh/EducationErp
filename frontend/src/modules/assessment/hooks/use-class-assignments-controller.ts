import { useClassAssignments, type Assignment } from "@/entities/assessment";
import { useCallback, useState } from "react";

/** "new" = đang giao bài mới, một Assignment = đang sửa bài đó. */
export type AssignmentFormTarget = Assignment | "new";

/** State của trang Bài tập & mini-test theo lớp - ui/* chỉ render (Mandate #2). */
export function useClassAssignmentsController(classId: string) {
  const board = useClassAssignments(classId);
  const [formTarget, setFormTarget] = useState<AssignmentFormTarget | null>(null);
  const [resultsTarget, setResultsTarget] = useState<Assignment | null>(null);

  const openCreate = useCallback(() => setFormTarget("new"), []);
  const openEdit = useCallback((assignment: Assignment) => setFormTarget(assignment), []);
  const closeForm = useCallback(() => setFormTarget(null), []);
  const openResults = useCallback((assignment: Assignment) => setResultsTarget(assignment), []);
  const closeResults = useCallback(() => setResultsTarget(null), []);

  return { board, formTarget, resultsTarget, openCreate, openEdit, closeForm, openResults, closeResults };
}
