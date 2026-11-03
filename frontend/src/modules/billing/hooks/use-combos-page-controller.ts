import { useCombos } from "@/entities/billing";
import { useBranchPaging } from "@/entities/branch";
import { useStudents } from "@/entities/student";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useCallback, useState } from "react";

const REFERENCE_PAGE_SIZE = 200;

/** Toàn bộ state/query của trang danh sách combo - ui/* chỉ render (Mandate #2). */
export function useCombosPageController() {
  const { branchId, page, setPage } = useBranchPaging();
  const [studentFilter, setStudentFilter] = useState("");
  const [createDialogOpen, setCreateDialogOpen] = useState(false);

  const combos = useCombos(
    page,
    DEFAULT_PAGE_SIZE,
    studentFilter.length === 0 ? undefined : studentFilter,
    branchId,
  );
  const students = useStudents(0, REFERENCE_PAGE_SIZE);

  const openCreateDialog = useCallback(() => setCreateDialogOpen(true), []);
  const closeCreateDialog = useCallback(() => setCreateDialogOpen(false), []);

  return {
    page,
    setPage,
    combos,
    students,
    studentFilter,
    setStudentFilter,
    createDialogOpen,
    openCreateDialog,
    closeCreateDialog,
  };
}
