import { useAccounts } from "@/entities/account";
import type { ClassSummary } from "@/entities/class";
import { useUpdateClass } from "@/modules/courses/api/use-courses-mutations";
import { updateClassFormSchema } from "@/modules/courses/model/courses-forms";
import { ScheduleSlotEditor } from "@/modules/courses/ui/schedule-slot-editor";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
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
  const updateClass = useUpdateClass(cls.id);
  const teachers = useAccounts(0, 100, null);

  const form = useZodForm({
    schema: updateClassFormSchema,
    initialValues: {
      teacherId: cls.teacherId,
      maxSeats: cls.maxSeats.toString(),
      active: cls.active,
      schedule: cls.schedule,
    },
    onSubmit: async (values) => {
      await updateClass.mutateAsync(values);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title={`Sửa lớp ${cls.code}`} description="Khóa học và chi nhánh bất biến, chỉ sửa giáo viên/sĩ số/lịch học/trạng thái.">
      <form id="edit-class-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Giáo viên" htmlFor="edit-class-teacher" error={form.fieldErrors.teacherId}>
          <GlassSelect
            id="edit-class-teacher"
            value={form.values.teacherId}
            invalid={form.fieldErrors.teacherId !== undefined}
            onChange={(event) => form.setValue("teacherId", event.target.value)}
          >
            {(teachers.data?.items ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.fullName}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField label="Sĩ số tối đa" htmlFor="edit-class-max-seats" error={form.fieldErrors.maxSeats}>
          <GlassInput
            id="edit-class-max-seats"
            type="number"
            min={1}
            value={form.values.maxSeats}
            invalid={form.fieldErrors.maxSeats !== undefined}
            onChange={(event) => form.setValue("maxSeats", event.target.value)}
          />
        </FormField>

        <FormField label="Lịch học hàng tuần" htmlFor="edit-class-schedule">
          <ScheduleSlotEditor slots={form.values.schedule} onChange={(slots) => form.setValue("schedule", slots)} />
        </FormField>

        <CheckableRow
          label="Đang hoạt động"
          description="Bỏ chọn để vô hiệu hoá lớp mà không xoá dữ liệu."
          checked={form.values.active}
          onToggle={() => form.setValue("active", !form.values.active)}
        />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="edit-class-form" loading={form.isSubmitting}>
          Lưu thay đổi
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
