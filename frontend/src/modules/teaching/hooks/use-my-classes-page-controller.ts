import { useMyTeachingClasses } from "@/entities/class";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useState } from "react";

/** Toàn bộ state trang "Lớp của tôi" của giáo viên - ui/* chỉ render (Mandate #2). */
export function useMyClassesPageController() {
  const [page, setPage] = useState(0);
  const query = useMyTeachingClasses(page, DEFAULT_PAGE_SIZE);

  return {
    data: query.data,
    isPending: query.isPending,
    error: query.isError ? query.error : null,
    isEmpty: query.isSuccess && query.data.items.length === 0,
    onPageChange: setPage,
  };
}
