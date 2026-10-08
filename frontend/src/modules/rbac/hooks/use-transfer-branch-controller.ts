import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useTransferBranch } from "@/modules/rbac/api/use-rbac-mutations";
import { transferBranchFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useTransferBranchController({
  account,
  onClose,
}: {
  readonly account: AccountSummary;
  readonly onClose: () => void;
}) {
  const catalog = useRbacCatalog();
  const transferBranch = useTransferBranch(account.id);

  const form = useZodForm({
    schema: transferBranchFormSchema,
    initialValues: { branchId: account.branchId ?? "" },
    onSubmit: async (values) => {
      await transferBranch.mutateAsync(values.branchId);
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    branches: catalog.data?.branches ?? [],
    handleSubmit: form.handleSubmit,
    setBranchId: (branchId: string) => form.setValue("branchId", branchId),
  };
}
