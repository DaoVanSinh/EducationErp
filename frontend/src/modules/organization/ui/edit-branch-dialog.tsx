import type { BranchSummary } from "@/entities/branch";
import { useUpdateBranch } from "@/modules/organization/api/use-organization-mutations";
import { editBranchFormSchema } from "@/modules/organization/model/organization-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditBranchDialogProps {
  readonly branch: BranchSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditBranchDialog({ branch, open, onClose }: EditBranchDialogProps) {
  const updateBranch = useUpdateBranch(branch.id);

  const form = useZodForm({
    schema: editBranchFormSchema,
    initialValues: { name: branch.name, address: branch.address ?? "", active: branch.active },
    onSubmit: async (values) => {
      await updateBranch.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Sửa chi nhánh ${branch.code}`}
      description="Mã chi nhánh bất biến, chỉ sửa được tên, địa chỉ và trạng thái hoạt động."
    >
      <form id="edit-branch-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tên chi nhánh" htmlFor="edit-branch-name" error={form.fieldErrors.name}>
          <GlassInput
            id="edit-branch-name"
            autoFocus
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Địa chỉ" htmlFor="edit-branch-address" hint="Không bắt buộc.">
          <GlassInput
            id="edit-branch-address"
            value={form.values.address}
            onChange={(event) => form.setValue("address", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá chi nhánh mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-branch-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
