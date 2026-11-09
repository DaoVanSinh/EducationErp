import { useTeachers } from "@/entities/teacher";
import { useMemo } from "react";

/** Đủ lớn để lấy hết giáo viên của một chi nhánh trong một lần. */
const TEACHER_OPTION_PAGE_SIZE = 100;

export interface TeacherOption {
  readonly accountId: string;
  readonly label: string;
}

/**
 * Lựa chọn cho ô "Giáo viên" khi mở/sửa lớp: giáo viên đang làm việc, đã có lương, thuộc đúng chi nhánh của lớp -
 * backend chặn y hệt (TeacherAssignmentGuard), đây là để người dùng không chọn được thứ chắc chắn bị từ chối. Giá trị
 * gửi lên là accountId vì `classes.teacher_id` tham chiếu tài khoản. `current` giữ giáo viên đang dạy trong danh sách
 * kể cả khi họ không còn đủ điều kiện: lớp cũ được giữ tới khi kết thúc.
 */
export function useTeacherOptions(branchId: string, current?: TeacherOption): readonly TeacherOption[] {
  const teachers = useTeachers(0, TEACHER_OPTION_PAGE_SIZE, branchId.length === 0 ? undefined : branchId);
  return useMemo(() => {
    const eligible = (teachers.data?.items ?? [])
      .filter((teacher) => teacher.active && teacher.payConfigured && teacher.homeBranchId === branchId)
      .map((teacher) => ({
        accountId: teacher.accountId,
        label: teacher.fullName ?? teacher.email ?? teacher.accountId,
      }));
    if (current === undefined || eligible.some((option) => option.accountId === current.accountId)) {
      return eligible;
    }
    return [current, ...eligible];
  }, [teachers.data, branchId, current]);
}
