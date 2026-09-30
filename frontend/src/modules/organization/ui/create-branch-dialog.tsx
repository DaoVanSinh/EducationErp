import { useCreateBranch } from "@/modules/organization/api/use-organization-mutations";
import { createBranchFormSchema } from "@/modules/organization/model/organization-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreateBranchDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateBranchDialog({ open, onClose }: CreateBranchDialogProps) {
  const createBranch = useCreateBranch();

  const form = useZodForm({
    schema: createBranchFormSchema,
    initialValues: { code: "", name: "", address: "" },
    onSubmit: async (values) => {
      await createBranch.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo chi nhánh"
      description="Mã chi nhánh không sửa được sau khi tạo, hãy đặt cẩn thận."
    >
      <form id="create-branch-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Mã chi nhánh"
          htmlFor="branch-code"
          hint="Chữ in hoa, số và dấu gạch dưới. Ví dụ: HCM01."
          error={form.fieldErrors.code}
        >
          <GlassInput
            id="branch-code"
            autoFocus
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Tên chi nhánh" htmlFor="branch-name" error={form.fieldErrors.name}>
          <GlassInput
            id="branch-name"
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Địa chỉ" htmlFor="branch-address" hint="Không bắt buộc.">
          <GlassInput
            id="branch-address"
            value={form.values.address}
            onChange={(event) => form.setValue("address", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-branch-form" loading={form.isSubmitting}>
          Tạo chi nhánh
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
