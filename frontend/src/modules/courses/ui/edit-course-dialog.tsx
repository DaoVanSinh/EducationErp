import type { CourseSummary } from "@/entities/course";
import { useUpdateCourse } from "@/modules/courses/api/use-courses-mutations";
import { updateCourseFormSchema } from "@/modules/courses/model/courses-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface EditCourseDialogProps {
  readonly course: CourseSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditCourseDialog({ course, open, onClose }: EditCourseDialogProps) {
  const updateCourse = useUpdateCourse(course.id);

  const form = useZodForm({
    schema: updateCourseFormSchema,
    initialValues: {
      name: course.name,
      description: course.description ?? "",
      standardSessionCount: course.standardSessionCount?.toString() ?? "",
      tuitionFee: course.tuitionFee?.toString() ?? "",
      active: course.active,
    },
    onSubmit: async (values) => {
      await updateCourse.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa khóa học ${course.code}`} description="Mã khóa học bất biến, chỉ sửa được các thông tin còn lại.">
      <form id="edit-course-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Tên khóa học" htmlFor="edit-course-name" error={form.fieldErrors.name}>
          <GlassInput
            id="edit-course-name"
            autoFocus
            value={form.values.name}
            invalid={form.fieldErrors.name !== undefined}
            onChange={(event) => form.setValue("name", event.target.value)}
          />
        </FormField>

        <FormField label="Mô tả" htmlFor="edit-course-description" hint="Không bắt buộc.">
          <GlassInput
            id="edit-course-description"
            value={form.values.description}
            onChange={(event) => form.setValue("description", event.target.value)}
          />
        </FormField>

        <FormField label="Số buổi chuẩn" htmlFor="edit-course-session-count" hint="Không bắt buộc.">
          <GlassInput
            id="edit-course-session-count"
            type="number"
            min={0}
            value={form.values.standardSessionCount}
            onChange={(event) => form.setValue("standardSessionCount", event.target.value)}
          />
        </FormField>

        <FormField label="Học phí toàn khoá (VND)" htmlFor="edit-course-tuition-fee" hint="Để trống nếu chưa chốt giá." error={form.fieldErrors.tuitionFee}>
          <GlassInput
            id="edit-course-tuition-fee"
            type="number"
            min={0}
            step={1000}
            value={form.values.tuitionFee}
            invalid={form.fieldErrors.tuitionFee !== undefined}
            onChange={(event) => form.setValue("tuitionFee", event.target.value)}
          />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá khóa học mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-course-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
