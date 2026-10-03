import { useAccounts } from "@/entities/account";
import { useCreateStudentProfile } from "@/modules/students/api/use-students-mutations";
import { createStudentFormSchema } from "@/modules/students/model/students-forms";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateStudentDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateStudentDialog({ open, onClose }: CreateStudentDialogProps) {
  const createStudent = useCreateStudentProfile();
  const accounts = useAccounts(0, DEFAULT_PAGE_SIZE, null);

  const form = useZodForm({
    schema: createStudentFormSchema,
    initialValues: { accountId: "", dateOfBirth: "", phone: "", sourceChannel: "" },
    onSubmit: async (values) => {
      await createStudent.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hồ sơ học viên" description="Chọn một tài khoản đã có vai trò Học viên.">
      <form id="create-student-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tài khoản" htmlFor="student-account" error={form.fieldErrors.accountId}>
          <GlassSelect
            id="student-account"
            value={form.values.accountId}
            invalid={form.fieldErrors.accountId !== undefined}
            onChange={(event) => form.setValue("accountId", event.target.value)}
          >
            <option value="">-- Chọn tài khoản --</option>
            {(accounts.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName} · {account.email}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Ngày sinh" htmlFor="student-dob" hint="Không bắt buộc.">
          <GlassInput
            id="student-dob"
            type="date"
            value={form.values.dateOfBirth}
            onChange={(event) => form.setValue("dateOfBirth", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="student-phone" hint="Không bắt buộc.">
          <GlassInput
            id="student-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Biết đến trung tâm qua đâu" htmlFor="student-source" hint="Không bắt buộc.">
          <GlassInput
            id="student-source"
            value={form.values.sourceChannel}
            onChange={(event) => form.setValue("sourceChannel", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-student-form" loading={form.isSubmitting}>
          Tạo hồ sơ
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
