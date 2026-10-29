import { useSession } from "@/entities/account";
import { usePermissions } from "@/entities/permission";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateStudent } from "@/modules/students/api/use-students-mutations";
import { createStudentFormSchema } from "@/modules/students/model/students-forms";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useZodForm } from "@/shared/lib/use-zod-form";

const EMPTY_VALUES = {
  email: "",
  fullName: "",
  homeBranchId: "",
  dateOfBirth: "",
  phone: "",
  sourceChannel: "",
};

/**
 * Tạo học viên một bước. Ai đọc được danh mục chi nhánh (quyền cấp tổ chức) thì chọn chi nhánh; người chỉ
 * có quyền theo chi nhánh thì học viên luôn thuộc chi nhánh của chính họ - backend ép như vậy, ở đây chỉ hiển
 * thị đúng điều đó thay vì một ô chọn vô nghĩa.
 */
export function useCreateStudentController({ onClose }: { readonly onClose: () => void }) {
  const createStudent = useCreateStudent();
  const permissions = usePermissions();
  const canChooseBranch = permissions.allows(ACCESS_RULE.readRole);
  const catalog = useRbacCatalog({ enabled: canChooseBranch });
  const session = useSession();

  const form = useZodForm({
    schema: createStudentFormSchema,
    initialValues: EMPTY_VALUES,
    onSubmit: async (values) => {
      await createStudent.mutateAsync(values);
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
    canChooseBranch,
    branches: catalog.data?.branches ?? [],
    fixedBranchLabel: session.data?.branchName ?? "Chi nhánh của bạn",
  };
}
