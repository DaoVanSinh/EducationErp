import { classApi, classKeys, type CreateClassPayload, type UpdateClassPayload } from "@/entities/class";
import { courseApi, courseKeys, type CreateCoursePayload, type UpdateCoursePayload } from "@/entities/course";
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
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: classKeys.all }),
      queryClient.invalidateQueries({ queryKey: courseKeys.all }),
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
