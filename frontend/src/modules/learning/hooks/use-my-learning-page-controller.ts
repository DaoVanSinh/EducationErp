import { useMyLearningClasses } from "@/modules/learning/api/use-learning-queries";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { useMemo } from "react";

/** Toàn bộ dữ liệu trang "Học tập của tôi" - ui/* chỉ render (Mandate #2). Lớp đang học xếp trước. */
export function useMyLearningPageController() {
  const query = useMyLearningClasses();

  const classes = useMemo(() => {
    const items = query.data ?? [];
    const active = items.filter((item) => item.status === ENROLLMENT_STATUS.active);
    const others = items.filter((item) => item.status !== ENROLLMENT_STATUS.active);
    return [...active, ...others];
  }, [query.data]);

  return {
    classes,
    isPending: query.isPending,
    error: query.isError ? query.error : null,
    isEmpty: query.isSuccess && classes.length === 0,
  };
}
