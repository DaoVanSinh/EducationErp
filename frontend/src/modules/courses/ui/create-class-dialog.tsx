import { useCreateClassController } from "@/modules/courses/hooks/use-create-class-controller";
import { ClassPlanFields } from "@/modules/courses/ui/class-plan-fields";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { listFieldError } from "@/shared/lib/use-zod-form";
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
  const controller = useCreateClassController({ onClose });
  const { values, fieldErrors, setValue } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Mở lớp"
      description="Lớp mở ở trạng thái Đang tuyển sinh, tự khai giảng vào ngày khai giảng nếu đủ sĩ số tối thiểu."
      className="max-w-2xl"
    >
      <form id="create-class-form" onSubmit={controller.handleSubmit} className="flex flex-col gap-4" noValidate>
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <FormField label="Khóa học" htmlFor="class-course" error={fieldErrors.courseId}>
            <GlassSelect
              id="class-course"
              value={values.courseId}
              invalid={fieldErrors.courseId !== undefined}
              onChange={(event) => setValue("courseId", event.target.value)}
            >
              <option value="">-- Chọn khóa học --</option>
              {controller.courses.map((course) => (
                <option key={course.id} value={course.id}>
                  {course.name}
                </option>
              ))}
            </GlassSelect>
          </FormField>
          <FormField label="Mã lớp" htmlFor="class-code" hint="Ví dụ: TA-GT-K15." error={fieldErrors.code}>
            <GlassInput
              id="class-code"
              value={values.code}
              invalid={fieldErrors.code !== undefined}
              onChange={(event) => setValue("code", event.target.value.toUpperCase())}
            />
          </FormField>
          <BranchSelect controller={controller} />
          <FormField label="Giáo viên" htmlFor="class-teacher" error={fieldErrors.teacherId}>
            <GlassSelect
              id="class-teacher"
              value={values.teacherId}
              invalid={fieldErrors.teacherId !== undefined}
              onChange={(event) => setValue("teacherId", event.target.value)}
            >
              <option value="">-- Chọn giáo viên --</option>
              {controller.teacherOptions.map((teacher) => (
                <option key={teacher.accountId} value={teacher.accountId}>
                  {teacher.label}
                </option>
              ))}
            </GlassSelect>
          </FormField>
        </div>

        <ClassPlanFields
          idPrefix="class"
          values={values}
          fieldErrors={fieldErrors}
          minStartDate={controller.minStartDate}
          endDateHint={controller.endDateHint}
          onChange={(field, value) => setValue(field, value)}
        />

        <FormField
          label="Lịch học hàng tuần"
          htmlFor="class-schedule"
          hint="Cần có lịch học trước khi khai giảng."
          error={listFieldError(fieldErrors, "schedule", "Buổi")}
        >
          <ScheduleSlotEditor slots={values.schedule} onChange={(slots) => setValue("schedule", slots)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-class-form" loading={controller.isSubmitting}>
          Mở lớp
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

function BranchSelect({ controller }: { readonly controller: ReturnType<typeof useCreateClassController> }) {
  const { values, fieldErrors, setValue } = controller;
  if (!controller.canChooseBranch) {
    return (
      <FormField label="Chi nhánh" htmlFor="class-branch" hint="Lớp mở tại chi nhánh bạn đang quản lý.">
        <GlassInput id="class-branch" value={controller.fixedBranchLabel} readOnly disabled />
      </FormField>
    );
  }
  return (
    <FormField label="Chi nhánh" htmlFor="class-branch" error={fieldErrors.branchId}>
      <GlassSelect
        id="class-branch"
        value={values.branchId}
        invalid={fieldErrors.branchId !== undefined}
        onChange={(event) => setValue("branchId", event.target.value)}
      >
        <option value="">-- Chọn chi nhánh --</option>
        {controller.branches.map((branch) => (
          <option key={branch.id} value={branch.id}>
            {branch.name}
          </option>
        ))}
      </GlassSelect>
    </FormField>
  );
}
