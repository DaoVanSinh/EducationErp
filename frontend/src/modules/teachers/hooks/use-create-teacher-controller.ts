import { useSession } from "@/entities/account";
import { usePermissions } from "@/entities/permission";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateTeacher } from "@/modules/teachers/api/use-teachers-mutations";
import { createTeacherFormSchema, EMPTY_PAY_VALUES } from "@/modules/teachers/model/teachers-forms";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { useZodForm } from "@/shared/lib/use-zod-form";
import type { z } from "zod";

const EMPTY_VALUES: z.input<typeof createTeacherFormSchema> = {
  email: "",
  fullName: "",
  homeBranchId: "",
  subjects: "",
  phone: "",
  bio: "",
  ...EMPTY_PAY_VALUES,
};

/**
 * Tạo giáo viên một bước. Admin gốc chọn chi nhánh chủ quản từ danh mục; admin chi nhánh không đọc được danh mục đó,
 * giáo viên luôn thuộc chi nhánh của họ (backend chặn chi nhánh khác). Mirror form mở lớp.
 */
export function useCreateTeacherController({ onClose }: { readonly onClose: () => void }) {
  const createTeacher = useCreateTeacher();
  const permissions = usePermissions();
  const canChooseBranch = permissions.allows(ACCESS_RULE.readRole);
  const catalog = useRbacCatalog({ enabled: canChooseBranch });
  const session = useSession();

  const form = useZodForm({
    schema: createTeacherFormSchema,
    initialValues: { ...EMPTY_VALUES, homeBranchId: canChooseBranch ? "" : (session.data?.branchId ?? "") },
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
    canChooseBranch,
    fixedBranchLabel: session.data?.branchName ?? "Chi nhánh của bạn",
    branches: catalog.data?.branches ?? [],
  };
}
