import { useMyAttendance } from "@/entities/attendance";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { useMemo } from "react";

/** Dữ liệu trang "Điểm danh của tôi" - lớp đang học xếp trước (Mandate #2). */
export function useMyAttendancePageController() {
  const query = useMyAttendance();

  const classes = useMemo(() => {
    const items = query.data ?? [];
    return [
      ...items.filter((item) => item.enrollmentStatus === ENROLLMENT_STATUS.active),
      ...items.filter((item) => item.enrollmentStatus !== ENROLLMENT_STATUS.active),
    ];
  }, [query.data]);

  return {
    classes,
    isPending: query.isPending,
    error: query.isError ? query.error : null,
    isEmpty: query.isSuccess && classes.length === 0,
  };
}
