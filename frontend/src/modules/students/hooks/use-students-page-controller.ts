import { useBranchPaging } from "@/entities/branch";
import { useStudents, type StudentProfileSummary } from "@/entities/student";
import { DEFAULT_PAGE_SIZE, SEARCH_DEBOUNCE_MS } from "@/shared/constants/query-config";
import { useDebouncedValue } from "@/shared/lib/use-debounced-value";
import { useCallback, useState } from "react";

/** Toàn bộ state trang Học viên - ui/* chỉ render (Mandate #2). */
export function useStudentsPageController() {
  const { branchId, page, setPage } = useBranchPaging();
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [editing, setEditing] = useState<StudentProfileSummary | null>(null);
  const [queryInput, setQueryInput] = useState("");
  const query = useDebouncedValue(queryInput.trim(), SEARCH_DEBOUNCE_MS);
  const students = useStudents(page, DEFAULT_PAGE_SIZE, branchId, query.length === 0 ? undefined : query);

  const onQueryChange = useCallback(
    (value: string) => {
      setQueryInput(value);
      setPage(0);
    },
    [setPage],
  );

  return {
    students,
    setPage,
    queryInput,
    onQueryChange,
    createDialogOpen,
    setCreateDialogOpen,
    editing,
    setEditing,
  };
}
