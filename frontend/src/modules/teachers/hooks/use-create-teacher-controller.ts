import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateTeacher } from "@/modules/teachers/api/use-teachers-mutations";
import { createTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

const EMPTY_VALUES = { email: "", fullName: "", homeBranchId: "", subjects: "", phone: "", bio: "" };

/**
 * Tạo giáo viên một bước. Tạo giáo viên là quyền cấp tổ chức (TEACHER:CREATE), nên người dùng ở đây luôn đọc
 * được danh mục chi nhánh để chọn chi nhánh chủ quản.
 */
export function useCreateTeacherController({ onClose }: { readonly onClose: () => void }) {
  const createTeacher = useCreateTeacher();
  const catalog = useRbacCatalog();

  const form = useZodForm({
    schema: createTeacherFormSchema,
    initialValues: EMPTY_VALUES,
    onSubmit: async (values) => {
      await createTeacher.mutateAsync(values);
      form.reset();
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
    branches: catalog.data?.branches ?? [],
  };
}
