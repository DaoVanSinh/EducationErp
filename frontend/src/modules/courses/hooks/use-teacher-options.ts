import { useTeachers } from "@/entities/teacher";
import { useMemo } from "react";

/** Đủ lớn để lấy hết giáo viên của một trung tâm trong một lần - mirror cỡ trang cũ của ô chọn. */
const TEACHER_OPTION_PAGE_SIZE = 100;

export interface TeacherOption {
  readonly accountId: string;
  readonly label: string;
}

/**
 * Lựa chọn cho ô "Giáo viên" khi mở/sửa lớp: CHỈ hồ sơ giáo viên đang hoạt động. Trước đây ô này
 * liệt kê mọi tài khoản (cả admin, học viên) - backend giờ cũng chặn (TeacherAssignmentGuard), đây
 * là để người dùng không chọn được thứ chắc chắn bị từ chối. Giá trị gửi lên là accountId vì
 * `classes.teacher_id` tham chiếu tài khoản, không phải hồ sơ.
 */
export function useTeacherOptions(): readonly TeacherOption[] {
  const teachers = useTeachers(0, TEACHER_OPTION_PAGE_SIZE);
  return useMemo(
    () =>
      (teachers.data?.items ?? [])
        .filter((teacher) => teacher.active)
        .map((teacher) => ({
          accountId: teacher.accountId,
          label: teacher.fullName ?? teacher.email ?? teacher.accountId,
        })),
    [teachers.data],
  );
}
