import type { TeacherPay } from "@/entities/teacher";
import { useUpdateTeacherPay } from "@/modules/teachers/api/use-teachers-mutations";
import { teacherPayFormSchema, toPayFormValues } from "@/modules/teachers/model/teachers-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export interface EditTeacherPayControllerOptions {
  readonly pay: TeacherPay;
  readonly onClose: () => void;
}

export function useEditTeacherPayController({ pay, onClose }: EditTeacherPayControllerOptions) {
  const updatePay = useUpdateTeacherPay(pay.profileId);

  return useZodForm({
    schema: teacherPayFormSchema,
    initialValues: toPayFormValues(pay),
    onSubmit: async (values) => {
      await updatePay.mutateAsync(values);
      onClose();
    },
  });
}
