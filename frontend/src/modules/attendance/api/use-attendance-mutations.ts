import { attendanceApi, attendanceKeys, type RecordAttendancePayload } from "@/entities/attendance";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/** Ghi một buổi xong phải tải lại cả bảng: tỷ lệ chuyên cần của từng học viên do backend tính. */
export function useRecordAttendance(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RecordAttendancePayload) => attendanceApi.recordSession(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: attendanceKeys.class(classId) });
    },
  });
}
