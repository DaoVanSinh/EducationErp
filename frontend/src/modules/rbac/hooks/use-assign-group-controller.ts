import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useAssignGroup } from "@/modules/rbac/api/use-rbac-mutations";
import { assignGroupFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useAssignGroupController({
  account,
  onClose,
}: {
  readonly account: AccountSummary;
  readonly onClose: () => void;
}) {
  const catalog = useRbacCatalog();
  const assignGroup = useAssignGroup(account.id);
  const joinedIds = new Set(account.groups.map((group) => group.id));
  const available = (catalog.data?.groups ?? []).filter((group) => !joinedIds.has(group.id));

  const form = useZodForm({
    schema: assignGroupFormSchema,
    initialValues: { groupId: "" },
    onSubmit: async (values) => {
      await assignGroup.mutateAsync(values.groupId);
      onClose();
    },
  });

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    available,
    handleSubmit: form.handleSubmit,
    setGroupId: (groupId: string) => form.setValue("groupId", groupId),
  };
}
