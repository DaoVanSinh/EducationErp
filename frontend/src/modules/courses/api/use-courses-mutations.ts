import { classApi, classKeys, type CreateClassPayload, type UpdateClassPayload } from "@/entities/class";
import { courseApi, courseKeys, type CreateCoursePayload, type UpdateCoursePayload } from "@/entities/course";
import { enrollmentKeys } from "@/entities/enrollment";
import { useMutation, useQueryClient } from "@tanstack/react-query";

function useCoursesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    await queryClient.invalidateQueries({ queryKey: courseKeys.all });
  };
}

function useClassesInvalidation(): () => Promise<void> {
  const queryClient = useQueryClient();
  return async () => {
    // Danh sách lớp hiển thị tên khóa học - đổi khóa học cũng có thể ảnh hưởng, nên bỏ cache cả hai.
    // Kết thúc/huỷ lớp đổi trạng thái mọi ghi danh của lớp - bỏ luôn cache ghi danh.
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: classKeys.all }),
      queryClient.invalidateQueries({ queryKey: courseKeys.all }),
      queryClient.invalidateQueries({ queryKey: enrollmentKeys.all }),
    ]);
  };
}

export function useCreateCourse() {
  const invalidate = useCoursesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateCoursePayload) => courseApi.createCourse(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateCourse(courseId: string) {
  const invalidate = useCoursesInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateCoursePayload) => courseApi.updateCourse(courseId, payload),
    onSuccess: invalidate,
  });
}

export function useCreateClass() {
  const invalidate = useClassesInvalidation();
  return useMutation({
    mutationFn: (payload: CreateClassPayload) => classApi.createClass(payload),
    onSuccess: invalidate,
  });
}

export function useUpdateClass(classId: string) {
  const invalidate = useClassesInvalidation();
  return useMutation({
    mutationFn: (payload: UpdateClassPayload) => classApi.updateClass(classId, payload),
    onSuccess: invalidate,
  });
}

/** Ba thao tác chuyển trạng thái lớp - khai giảng, kết thúc, huỷ. */
export const CLASS_TRANSITION = {
  start: "START",
  complete: "COMPLETE",
  cancel: "CANCEL",
} as const;

export type ClassTransition = (typeof CLASS_TRANSITION)[keyof typeof CLASS_TRANSITION];

const TRANSITION_CALL: Record<ClassTransition, (classId: string) => Promise<void>> = {
  [CLASS_TRANSITION.start]: (classId) => classApi.startClass(classId),
  [CLASS_TRANSITION.complete]: (classId) => classApi.completeClass(classId),
  [CLASS_TRANSITION.cancel]: (classId) => classApi.cancelClass(classId),
};

export function useClassTransition() {
  const invalidate = useClassesInvalidation();
  return useMutation({
    mutationFn: ({ classId, transition }: { classId: string; transition: ClassTransition }) =>
      TRANSITION_CALL[transition](classId),
    onSuccess: invalidate,
  });
}
