import type { AccountSummary } from "@/entities/account";
import { useTransferBranchController } from "@/modules/rbac/hooks/use-transfer-branch-controller";
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
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    branches,
    handleSubmit,
    setBranchId,
  } = useTransferBranchController({ account, onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Chuyển chi nhánh"
      description={`${account.fullName} · hiện tại: ${account.branchName ?? "chưa thuộc chi nhánh nào"}`}
    >
      <form id="transfer-branch-form" onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <FormField
          label="Chi nhánh mới"
          htmlFor="transfer-branch-id"
          hint="Chi nhánh quyết định phạm vi mà các quyền cấp chi nhánh nhìn thấy."
          error={fieldErrors.branchId}
        >
          <GlassSelect
            id="transfer-branch-id"
            value={values.branchId}
            invalid={fieldErrors.branchId !== undefined}
            onChange={(event) => setBranchId(event.target.value)}
          >
            <option value="">-- Chọn chi nhánh --</option>
            {branches.map((branch) => (
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
        <GlassButton type="submit" form="transfer-branch-form" loading={isSubmitting}>
          Chuyển
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
