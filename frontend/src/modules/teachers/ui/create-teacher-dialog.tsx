import { useAccounts } from "@/entities/account";
import { useCreateTeacherProfile } from "@/modules/teachers/api/use-teachers-mutations";
import { createTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { DEFAULT_PAGE_SIZE } from "@/shared/constants/query-config";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateTeacherDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateTeacherDialog({ open, onClose }: CreateTeacherDialogProps) {
  const createTeacher = useCreateTeacherProfile();
  const accounts = useAccounts(0, DEFAULT_PAGE_SIZE, null);

  const form = useZodForm({
    schema: createTeacherFormSchema,
    initialValues: { accountId: "", subjects: "", phone: "", bio: "" },
    onSubmit: async (values) => {
      await createTeacher.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hồ sơ giáo viên" description="Chọn một tài khoản đã có vai trò Giáo viên.">
      <form id="create-teacher-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tài khoản" htmlFor="teacher-account" error={form.fieldErrors.accountId}>
          <GlassSelect
            id="teacher-account"
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

        <FormField label="Môn/chuyên môn dạy" htmlFor="teacher-subjects" hint="Cách nhau bởi dấu phẩy, ví dụ: Tiếng Anh, IELTS">
          <GlassInput
            id="teacher-subjects"
            value={form.values.subjects}
            onChange={(event) => form.setValue("subjects", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="teacher-phone" hint="Không bắt buộc.">
          <GlassInput
            id="teacher-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Giới thiệu ngắn" htmlFor="teacher-bio" hint="Không bắt buộc.">
          <GlassInput
            id="teacher-bio"
            value={form.values.bio}
            onChange={(event) => form.setValue("bio", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-teacher-form" loading={form.isSubmitting}>
          Tạo hồ sơ
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
