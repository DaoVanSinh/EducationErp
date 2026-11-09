import { useMyLearningClasses } from "@/modules/learning/api/use-learning-queries";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useMemo } from "react";

/**
 * Toàn bộ dữ liệu trang "Học tập của tôi" - ui/* chỉ render (Mandate #2). Lớp đã kết thúc hoặc đã rút vào mục lịch sử,
 * vẫn xem được điểm và thông tin cơ bản.
 */
export function useMyLearningPageController() {
  const query = useMyLearningClasses();

  const items = useMemo(() => query.data ?? [], [query.data]);
  const classes = useMemo(() => items.filter((item) => item.status === ENROLLMENT_STATUS.active), [items]);
  const history = useMemo(() => items.filter((item) => item.status !== ENROLLMENT_STATUS.active), [items]);

  return {
    classes,
    history,
    today: todayLocalIsoDate(),
    isPending: query.isPending,
    error: query.isError ? query.error : null,
    isEmpty: query.isSuccess && items.length === 0,
  };
}
