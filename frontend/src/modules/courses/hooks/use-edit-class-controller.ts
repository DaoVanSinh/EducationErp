import { CLASS_STATUS, type ClassSummary } from "@/entities/class";
import { useCourses } from "@/entities/course";
import { useUpdateClass } from "@/modules/courses/api/use-courses-mutations";
import { useTeacherOptions } from "@/modules/courses/hooks/use-teacher-options";
import { planEndDate } from "@/modules/courses/model/class-end-date-plan";
import { updateClassFormSchema } from "@/modules/courses/model/courses-forms";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useMemo } from "react";

/** Cùng cỡ trang với form mở lớp - đủ lấy hết danh mục khoá học trong một lần (dùng chung cache). */
const COURSE_OPTION_PAGE_SIZE = 100;
const AUTO_END_DATE_FIELDS: ReadonlySet<string> = new Set(["startDate", "schedule"]);

/**
 * Sửa lớp còn mở. Ngày khai giảng và hai mức sĩ số là điều kiện tuyển sinh - chỉ sửa được khi lớp còn đang
 * tuyển sinh (backend chặn y hệt); khi lớp đã học thì các ô này khoá lại để người dùng không gửi thứ chắc chắn
 * bị từ chối.
 */
export function useEditClassController({ cls, onClose }: { readonly cls: ClassSummary; readonly onClose: () => void }) {
  const updateClass = useUpdateClass(cls.id);
  const currentTeacher = useMemo(
    () => ({ accountId: cls.teacherId, label: cls.teacherName ?? "Giáo viên hiện tại" }),
    [cls.teacherId, cls.teacherName],
  );
  const teacherOptions = useTeacherOptions(cls.branchId, currentTeacher);
  const enrolling = cls.status === CLASS_STATUS.enrolling;
  const courses = useCourses(0, COURSE_OPTION_PAGE_SIZE);

  const form = useZodForm({
    schema: updateClassFormSchema,
    initialValues: {
      teacherId: cls.teacherId,
      teacherEffectiveFrom: todayLocalIsoDate(),
      maxSeats: cls.maxSeats.toString(),
      minSeats: cls.minSeats.toString(),
      startDate: cls.startDate,
      endDate: cls.endDate ?? "",
      schedule: cls.schedule,
    },
    onSubmit: async (values) => {
      // Chỉ lớp đã khai giảng mới cần "từ ngày": lớp chưa học thì giáo viên mới dạy từ buổi đầu.
      const replacing = !enrolling && values.teacherId !== cls.teacherId;
      await updateClass.mutateAsync({
        ...values,
        teacherEffectiveFrom: replacing ? values.teacherEffectiveFrom : null,
      });
      onClose();
    },
  });

  const sessionCount =
    courses.data?.items.find((course) => course.id === cls.courseId)?.standardSessionCount ?? null;

  // Đổi ngày khai giảng hoặc lịch thì tính lại ngày kết thúc; khoá học của lớp không đổi được.
  const setValue: typeof form.setValue = (field, value) => {
    form.setValue(field, value);
    if (!AUTO_END_DATE_FIELDS.has(String(field))) {
      return;
    }
    const next = { ...form.values, [field]: value };
    const plan = planEndDate(next.startDate, next.schedule, sessionCount);
    if (plan.endDate !== null) {
      form.setValue("endDate", plan.endDate);
    }
  };

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setValue,
    endDateHint: planEndDate(form.values.startDate, form.values.schedule, sessionCount).hint,
    teacherOptions,
    showTeacherEffectiveFrom: !enrolling && form.values.teacherId !== cls.teacherId,
    minTeacherEffectiveFrom: cls.startDate,
    today: todayLocalIsoDate(),
    teacherOutsideBranch: cls.teacherOutsideBranch,
    enrollmentTermsLocked: !enrolling,
    minMaxSeats: Math.max(cls.activeEnrollments, 1),
  };
}
