import { useMyAssessments } from "@/entities/assessment";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useMemo, useState } from "react";

/** Dữ liệu trang "Bài tập & luyện tập" - lớp đang học xếp trước (Mandate #2). */
export function useMyAssessmentsPageController() {
  const query = useMyAssessments();

  const classes = useMemo(() => {
    const items = query.data ?? [];
    return [
      ...items.filter((item) => item.enrollmentStatus === ENROLLMENT_STATUS.active),
      ...items.filter((item) => item.enrollmentStatus !== ENROLLMENT_STATUS.active),
    ];
  }, [query.data]);

  /** Mốc để đánh dấu bài quá hạn chưa nộp. */
  const [today] = useState(todayLocalIsoDate);

  return {
    classes,
    today,
    isPending: query.isPending,
    error: query.isError ? query.error : null,
    isEmpty: query.isSuccess && classes.length === 0,
  };
}
