import type { TeacherProfileSummary } from "@/entities/teacher";
import { useUpdateTeacherProfile } from "@/modules/teachers/api/use-teachers-mutations";
import { updateTeacherFormSchema } from "@/modules/teachers/model/teachers-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditTeacherDialogProps {
  readonly teacher: TeacherProfileSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditTeacherDialog({ teacher, open, onClose }: EditTeacherDialogProps) {
  const updateTeacher = useUpdateTeacherProfile(teacher.id);

  const form = useZodForm({
    schema: updateTeacherFormSchema,
    initialValues: {
      subjects: teacher.subjects.join(", "),
      phone: teacher.phone ?? "",
      bio: teacher.bio ?? "",
      active: teacher.active,
    },
    onSubmit: async (values) => {
      await updateTeacher.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa hồ sơ ${teacher.fullName ?? ""}`}>
      <form id="edit-teacher-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Môn/chuyên môn dạy" htmlFor="edit-teacher-subjects" hint="Cách nhau bởi dấu phẩy.">
          <GlassInput
            id="edit-teacher-subjects"
            value={form.values.subjects}
            onChange={(event) => form.setValue("subjects", event.target.value)}
          />
        </FormField>

        <FormField label="Số điện thoại" htmlFor="edit-teacher-phone" hint="Không bắt buộc.">
          <GlassInput
            id="edit-teacher-phone"
            value={form.values.phone}
            onChange={(event) => form.setValue("phone", event.target.value)}
          />
        </FormField>

        <FormField label="Giới thiệu ngắn" htmlFor="edit-teacher-bio" hint="Không bắt buộc.">
          <GlassInput
            id="edit-teacher-bio"
            value={form.values.bio}
            onChange={(event) => form.setValue("bio", event.target.value)}
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
        <GlassButton type="submit" form="edit-teacher-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
