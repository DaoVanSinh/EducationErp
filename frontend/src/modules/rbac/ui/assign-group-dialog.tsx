import type { AccountSummary } from "@/entities/account";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useAssignGroup } from "@/modules/rbac/api/use-rbac-mutations";
import { assignGroupFormSchema } from "@/modules/rbac/model/rbac-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
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

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Thêm vào nhóm người dùng"
      description={`${account.fullName} · ${account.email}`}
    >
      <form id="assign-group-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Nhóm người dùng"
          htmlFor="assign-group-id"
          hint="Quyền của nhóm có hiệu lực ngay sau khi lưu."
          error={form.fieldErrors.groupId}
        >
          <GlassSelect
            id="assign-group-id"
            value={form.values.groupId}
            invalid={form.fieldErrors.groupId !== undefined}
            onChange={(event) => form.setValue("groupId", event.target.value)}
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
          <p className="text-xs text-mist-500">
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
          loading={form.isSubmitting}
          disabled={available.length === 0}
        >
          Thêm vào nhóm
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
