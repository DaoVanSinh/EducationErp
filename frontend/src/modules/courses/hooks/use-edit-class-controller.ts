import { CLASS_STATUS, type ClassSummary } from "@/entities/class";
import { useUpdateClass } from "@/modules/courses/api/use-courses-mutations";
import { useTeacherOptions } from "@/modules/courses/hooks/use-teacher-options";
import { updateClassFormSchema } from "@/modules/courses/model/courses-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

/**
 * Sửa lớp còn mở. Ngày khai giảng và sĩ số tối thiểu là điều kiện khai giảng - chỉ sửa được khi lớp còn đang
 * tuyển sinh (backend chặn y hệt); khi lớp đã học thì hai ô này khoá lại để người dùng không gửi thứ chắc chắn
 * bị từ chối.
 */
export function useEditClassController({ cls, onClose }: { readonly cls: ClassSummary; readonly onClose: () => void }) {
  const updateClass = useUpdateClass(cls.id);
  const teacherOptions = useTeacherOptions();

  const form = useZodForm({
    schema: updateClassFormSchema,
    initialValues: {
      teacherId: cls.teacherId,
      maxSeats: cls.maxSeats.toString(),
      minSeats: cls.minSeats.toString(),
      startDate: cls.startDate,
      endDate: cls.endDate ?? "",
      schedule: cls.schedule,
    },
    onSubmit: async (values) => {
      await updateClass.mutateAsync(values);
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setValue: form.setValue,
    teacherOptions,
    enrollmentTermsLocked: cls.status !== CLASS_STATUS.enrolling,
    minMaxSeats: Math.max(cls.activeEnrollments, 1),
  };
}
