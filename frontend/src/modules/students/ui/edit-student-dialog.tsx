import type { StudentProfileSummary } from "@/entities/student";
import { useUpdateStudentProfile } from "@/modules/students/api/use-students-mutations";
import { updateStudentFormSchema } from "@/modules/students/model/students-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditStudentDialogProps {
  readonly student: StudentProfileSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditStudentDialog({ student, open, onClose }: EditStudentDialogProps) {
  const updateStudent = useUpdateStudentProfile(student.id);

  const form = useZodForm({
    schema: updateStudentFormSchema,
    initialValues: {
      dateOfBirth: student.dateOfBirth ?? "",
      phone: student.phone ?? "",
      sourceChannel: student.sourceChannel ?? "",
      active: student.active,
    },
    onSubmit: async (values) => {
      await updateStudent.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa hồ sơ ${student.fullName ?? ""}`}>
      <form id="edit-student-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ngày sinh" htmlFor="edit-student-dob" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-dob"
            type="date"
            value={form.values.dateOfBirth}
            onChange={(event) => form.setValue("dateOfBirth", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="edit-student-phone" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Biết đến trung tâm qua đâu" htmlFor="edit-student-source" hint="Không bắt buộc.">
          <GlassInput
            id="edit-student-source"
            value={form.values.sourceChannel}
            onChange={(event) => form.setValue("sourceChannel", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá hồ sơ mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-student-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
