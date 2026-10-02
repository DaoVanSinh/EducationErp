import { useAccounts } from "@/entities/account";
import { useCourses } from "@/entities/course";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateClass } from "@/modules/courses/api/use-courses-mutations";
import { createClassFormSchema } from "@/modules/courses/model/courses-forms";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateClassDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateClassDialog({ open, onClose }: CreateClassDialogProps) {
  const createClass = useCreateClass();
  const courses = useCourses(0, 100);
  const catalog = useRbacCatalog();
  const teachers = useAccounts(0, 100, null);

  const form = useZodForm({
    schema: createClassFormSchema,
    initialValues: { courseId: "", code: "", branchId: "", teacherId: "", maxSeats: "", schedule: [] },
    onSubmit: async (values) => {
      await createClass.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Mở lớp" description="Khóa học và chi nhánh không sửa được sau khi tạo.">
      <form id="create-class-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Khóa học" htmlFor="class-course" error={form.fieldErrors.courseId}>
          <GlassSelect
            id="class-course"
            value={form.values.courseId}
            invalid={form.fieldErrors.courseId !== undefined}
            onChange={(event) => form.setValue("courseId", event.target.value)}
          >
            <option value="">-- Chọn khóa học --</option>
            {(courses.data?.items ?? []).map((course) => (
              <option key={course.id} value={course.id}>
                {course.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Mã lớp" htmlFor="class-code" hint="Ví dụ: TA-GT-K15." error={form.fieldErrors.code}>
          <GlassInput
            id="class-code"
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Chi nhánh" htmlFor="class-branch" error={form.fieldErrors.branchId}>
          <GlassSelect
            id="class-branch"
            value={form.values.branchId}
            invalid={form.fieldErrors.branchId !== undefined}
            onChange={(event) => form.setValue("branchId", event.target.value)}
          >
            <option value="">-- Chọn chi nhánh --</option>
            {(catalog.data?.branches ?? []).map((branch) => (
              <option key={branch.id} value={branch.id}>
                {branch.name}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Giáo viên" htmlFor="class-teacher" error={form.fieldErrors.teacherId}>
          <GlassSelect
            id="class-teacher"
            value={form.values.teacherId}
            invalid={form.fieldErrors.teacherId !== undefined}
            onChange={(event) => form.setValue("teacherId", event.target.value)}
          >
            <option value="">-- Chọn giáo viên --</option>
            {(teachers.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Sĩ số tối đa" htmlFor="class-max-seats" error={form.fieldErrors.maxSeats}>
          <GlassInput
            id="class-max-seats"
            type="number"
            min={1}
            value={form.values.maxSeats}
            invalid={form.fieldErrors.maxSeats !== undefined}
            onChange={(event) => form.setValue("maxSeats", event.target.value)}
          />
        </FormField>

        <FormField label="Lịch học hàng tuần" htmlFor="class-schedule" hint="Có thể để trống rồi thêm sau.">
          <ScheduleSlotEditor slots={form.values.schedule} onChange={(slots) => form.setValue("schedule", slots)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-class-form" loading={form.isSubmitting}>
          Mở lớp
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
