import { academicApi, academicKeys, type RecordGradePayload } from "@/entities/academic";
import { useMutation, useQueryClient } from "@tanstack/react-query";

/**
 * Nhập/sửa điểm xong phải bỏ cache CẢ sổ điểm của lớp: điểm tổng kết của dòng vừa sửa do backend
 * tính (null khi còn thiếu cột), nên không thể cập nhật lạc quan ở client mà không có nguy cơ hiện
 * một con số sai trong khoảnh khắc.
 */
export function useRecordGrade(classId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: RecordGradePayload) => academicApi.recordGrade(payload),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: academicKeys.gradebook(classId) });
    },
  });
}
