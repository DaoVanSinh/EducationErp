import { useBranchPaging } from "@/entities/branch";
import { useTeachers, type TeacherProfileSummary } from "@/entities/teacher";
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from "@/shared/constants/query-config";
import { useDebouncedValue } from "@/shared/lib/use-debounced-value";
import { useCallback, useState } from "react";

/** Toàn bộ state trang Giáo viên - ui/* chỉ render (Mandate #2). */
export function useTeachersPageController() {
  const { branchId, page, setPage } = useBranchPaging();
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<TeacherProfileSummary | null>(null);
  const [queryInput, setQueryInput] = useState("");
  const query = useDebouncedValue(queryInput.trim(), SEARCH_DEBOUNCE_MS);
  const teachers = useTeachers(page, DEFAULT_PAGE_SIZE, branchId, query.length === 0 ? undefined : query);

  const onQueryChange = useCallback(
    (value: string) => {
      setQueryInput(value);
      setPage(0);
    },
    [setPage],
  );

  return {
    teachers,
    setPage,
    queryInput,
    onQueryChange,
    createDialogOpen,
    openCreateDialog: () => setCreateDialogOpen(true),
    closeCreateDialog: () => setCreateDialogOpen(false),
    editing,
    setEditing,
    closeEdit: () => setEditing(null),
  };
}
