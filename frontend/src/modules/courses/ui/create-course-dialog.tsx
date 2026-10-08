import { useCreateCourse } from "@/modules/courses/api/use-courses-mutations";
import { createCourseFormSchema } from "@/modules/courses/model/courses-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreateCourseDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateCourseDialog({ open, onClose }: CreateCourseDialogProps) {
  const createCourse = useCreateCourse();

  const form = useZodForm({
    schema: createCourseFormSchema,
    initialValues: { code: "", name: "", description: "", standardSessionCount: "", tuitionFee: "" },
    onSubmit: async (values) => {
      await createCourse.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo khóa học" description="Mã khóa học không sửa được sau khi tạo.">
      <form id="create-course-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Mã khóa học" htmlFor="course-code" hint="Chữ in hoa, số và dấu gạch ngang. Ví dụ: TA-GT." error={form.fieldErrors.code}>
          <GlassInput
            id="course-code"
            autoFocus
            value={form.values.code}
            invalid={form.fieldErrors.code !== undefined}
            onChange={(event) => form.setValue("code", event.target.value.toUpperCase())}
          />
        </FormField>

        <FormField label="Tên khóa học" htmlFor="course-name" error={form.fieldErrors.name}>
          <GlassInput
            id="course-name"
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Mô tả" htmlFor="course-description" hint="Không bắt buộc.">
          <GlassInput
            id="course-description"
            value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)}
          />
        </FormField>

        <FormField label="Số buổi chuẩn" htmlFor="course-session-count" hint="Không bắt buộc.">
          <GlassInput
            id="course-session-count"
            type="number"
            min={0}
            value={form.values.standardSessionCount}
            onChange={(event) => form.setValue("standardSessionCount", event.target.value)}
          />
        </FormField>

        <FormField label="Học phí toàn khoá (VND)" htmlFor="course-tuition-fee" hint="Không bắt buộc. Để trống nếu chưa chốt giá." error={form.fieldErrors.tuitionFee}>
          <GlassInput
            id="course-tuition-fee"
            type="number"
            min={0}
            step={1000}
            value={form.values.tuitionFee}
            invalid={form.fieldErrors.tuitionFee !== undefined}
            onChange={(event) => form.setValue("tuitionFee", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-course-form" loading={form.isSubmitting}>
          Tạo khóa học
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
