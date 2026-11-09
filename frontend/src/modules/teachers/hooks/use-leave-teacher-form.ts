import { useLeaveTeacher } from "@/modules/teachers/api/use-teachers-mutations";
import { leaveTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useZodForm } from "@/shared/lib/use-zod-form";

/** Cho nghỉ: backend chặn khi còn lớp mở, lỗi đó hiện nguyên văn để người dùng biết cần bàn giao lớp nào trước. */
export function useLeaveTeacherForm(profileId: string, onClose: () => void) {
  const leaveTeacher = useLeaveTeacher(profileId);
  return useZodForm({
    schema: leaveTeacherFormSchema,
    initialValues: { lastWorkDate: todayLocalIsoDate() },
    onSubmit: async (values) => {
      await leaveTeacher.mutateAsync(values);
      onClose();
    },
  });
}
