import type { AccountSummary } from "@/entities/account";
import { useAssignGroupController } from "@/modules/rbac/hooks/use-assign-group-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface AssignGroupDialogProps {
  readonly account: AccountSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function AssignGroupDialog({ account, open, onClose }: AssignGroupDialogProps) {
  const {
    values,
    fieldErrors,
    submitError,
    isSubmitting,
    available,
    handleSubmit,
    setGroupId,
  } = useAssignGroupController({ account, onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm vào nhóm người dùng"
      description={`${account.fullName} · ${account.email}`}
    >
      <form id="assign-group-form" onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError ? <ErrorNotice error={submitError} /> : null}

        <FormField
          label="Nhóm người dùng"
          htmlFor="assign-group-id"
          error={fieldErrors.groupId}
        >
          <GlassSelect
            id="assign-group-id"
            value={values.groupId}
            invalid={fieldErrors.groupId !== undefined}
            onChange={(event) => setGroupId(event.target.value)}
          >
            <option value="">
              {available.length === 0 ? "Không còn nhóm nào để thêm" : "-- Chọn nhóm --"}
            </option>
            {available.map((group) => (
              <option key={group.id} value={group.id}>
                {group.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        {account.groups.length > 0 ? (
          <p className="text-xs text-slate-500">
            Đang thuộc: {account.groups.map((group) => group.name).join(", ")}
          </p>
        ) : null}
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton
          type="submit"
          form="assign-group-form"
          loading={isSubmitting}
          disabled={available.length === 0}
        >
          Thêm vào nhóm
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
