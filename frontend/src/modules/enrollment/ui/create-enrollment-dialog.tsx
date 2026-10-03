import type { ClassSummary } from "@/entities/class";
import type { StudentProfileSummary } from "@/entities/student";
import { useCreateEnrollment } from "@/modules/enrollment/api/use-enrollment-mutations";
import { createEnrollmentFormSchema } from "@/modules/enrollment/model/enrollment-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateEnrollmentDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly students: readonly StudentProfileSummary[];
  readonly classes: readonly ClassSummary[];
}

export function CreateEnrollmentDialog({ open, onClose, students, classes }: CreateEnrollmentDialogProps) {
  const createEnrollment = useCreateEnrollment();

  const form = useZodForm({
    schema: createEnrollmentFormSchema,
    initialValues: { studentProfileId: "", classId: "" },
    onSubmit: async (values) => {
      await createEnrollment.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Ghi danh học viên"
      description="Một học viên chỉ có một ghi danh đang học cho mỗi lớp. Lớp đã đủ chỗ sẽ bị từ chối."
    >
      <form id="create-enrollment-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Học viên" htmlFor="enrollment-student" error={form.fieldErrors.studentProfileId}>
          <GlassSelect
            id="enrollment-student"
            className="w-full"
            containerClassName="w-full"
            value={form.values.studentProfileId}
            invalid={form.fieldErrors.studentProfileId !== undefined}
            onChange={(event) => form.setValue("studentProfileId", event.target.value)}
          >
            <option value="">Chọn học viên</option>
            {students
              .filter((student) => student.active)
              .map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField
          label="Lớp học"
          htmlFor="enrollment-class"
          hint="Chỉ hiện lớp đang hoạt động."
          error={form.fieldErrors.classId}
        >
          <GlassSelect
            id="enrollment-class"
            className="w-full"
            containerClassName="w-full"
            value={form.values.classId}
            invalid={form.fieldErrors.classId !== undefined}
            onChange={(event) => form.setValue("classId", event.target.value)}
          >
            <option value="">Chọn lớp</option>
            {classes
              .filter((item) => item.active)
              .map((item) => (
                <option key={item.id} value={item.id}>
                  {item.code} — {item.courseName} ({item.maxSeats} chỗ)
                </option>
              ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-enrollment-form" loading={form.isSubmitting}>
          Ghi danh
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
