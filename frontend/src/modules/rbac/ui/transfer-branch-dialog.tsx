import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useTransferBranch } from "@/modules/rbac/api/use-rbac-mutations";
import { transferBranchFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface TransferBranchDialogProps {
  readonly account: AccountSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function TransferBranchDialog({ account, open, onClose }: TransferBranchDialogProps) {
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

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Chuyển chi nhánh"
      description={`${account.fullName} · hiện tại: ${account.branchName ?? "chưa thuộc chi nhánh nào"}`}
    >
      <form id="transfer-branch-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Chi nhánh mới"
          htmlFor="transfer-branch-id"
          hint="Chi nhánh quyết định phạm vi mà các quyền cấp chi nhánh nhìn thấy."
          error={form.fieldErrors.branchId}
        >
          <GlassSelect
            id="transfer-branch-id"
            value={form.values.branchId}
            invalid={form.fieldErrors.branchId !== undefined}
            onChange={(event) => form.setValue("branchId", event.target.value)}
          >
            <option value="">-- Chọn chi nhánh --</option>
            {(catalog.data?.branches ?? []).map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="transfer-branch-form" loading={form.isSubmitting}>
          Chuyển
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
