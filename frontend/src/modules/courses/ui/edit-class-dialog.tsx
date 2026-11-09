import type { ClassSummary } from "@/entities/class";
import { useEditClassController } from "@/modules/courses/hooks/use-edit-class-controller";
import { ClassPlanFields } from "@/modules/courses/ui/class-plan-fields";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { listFieldError } from "@/shared/lib/use-zod-form";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
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

        {controller.teacherOutsideBranch ? (
          <p className="rounded-2xl border border-amber-200/90 bg-amber-50 p-3 text-xs text-amber-700">
            Giáo viên hiện tại thuộc chi nhánh khác. Lớp được giữ tới khi kết thúc; nếu đổi giáo viên thì chỉ chọn được người
            của chi nhánh này.
          </p>
        ) : null}

        <FormField
          label="Giáo viên"
          htmlFor="edit-class-teacher"
          hint="Giáo viên thuộc chi nhánh của lớp, đã thiết lập lương."
          error={fieldErrors.teacherId}
        >
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

        {controller.showTeacherEffectiveFrom ? (
          <FormField
            label="Giáo viên mới dạy từ ngày"
            htmlFor="edit-class-teacher-from"
            hint="Buổi trước ngày này vẫn tính giờ cho giáo viên cũ, kể cả khi điểm danh bù sau này."
            error={fieldErrors.teacherEffectiveFrom}
          >
            <GlassInput
              id="edit-class-teacher-from"
              type="date"
              min={controller.minTeacherEffectiveFrom}
              max={controller.today}
              value={values.teacherEffectiveFrom}
              invalid={fieldErrors.teacherEffectiveFrom !== undefined}
              onChange={(event) => setValue("teacherEffectiveFrom", event.target.value)}
            />
          </FormField>
        ) : null}

        <ClassPlanFields
          idPrefix="edit-class"
          values={values}
          fieldErrors={fieldErrors}
          enrollmentTermsLocked={controller.enrollmentTermsLocked}
          minMaxSeats={controller.minMaxSeats}
          endDateHint={controller.endDateHint}
          onChange={(field, value) => setValue(field, value)}
        />

        <FormField
          label="Lịch học hàng tuần"
          htmlFor="edit-class-schedule"
          error={listFieldError(fieldErrors, "schedule", "Buổi")}
        >
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
