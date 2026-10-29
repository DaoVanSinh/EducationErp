import type { ClassSummary } from "@/entities/class";
import { useEditClassController } from "@/modules/courses/hooks/use-edit-class-controller";
import { ClassPlanFields } from "@/modules/courses/ui/class-plan-fields";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface EditClassDialogProps {
  readonly cls: ClassSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditClassDialog({ cls, open, onClose }: EditClassDialogProps) {
  const controller = useEditClassController({ cls, onClose });
  const { values, fieldErrors, setValue } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Sửa lớp ${cls.code}`}
      description="Khóa học và chi nhánh bất biến. Trạng thái lớp đổi bằng các nút Khai giảng / Kết thúc / Huỷ lớp."
      className="max-w-2xl"
    >
      <form id="edit-class-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <FormField label="Giáo viên" htmlFor="edit-class-teacher" error={fieldErrors.teacherId}>
          <GlassSelect
            id="edit-class-teacher"
            value={values.teacherId}
            invalid={fieldErrors.teacherId !== undefined}
            onChange={(event) => setValue("teacherId", event.target.value)}
          >
            {controller.teacherOptions.map((teacher) => (
              <option key={teacher.accountId} value={teacher.accountId}>
                {teacher.label}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <ClassPlanFields
          idPrefix="edit-class"
          values={values}
          fieldErrors={fieldErrors}
          enrollmentTermsLocked={controller.enrollmentTermsLocked}
          minMaxSeats={controller.minMaxSeats}
          onChange={(field, value) => setValue(field, value)}
        />

        <FormField label="Lịch học hàng tuần" htmlFor="edit-class-schedule">
          <ScheduleSlotEditor slots={values.schedule} onChange={(slots) => setValue("schedule", slots)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-class-form" loading={controller.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
