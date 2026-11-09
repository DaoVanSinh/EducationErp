import { useCreatePayrollRunController } from "@/modules/payroll/hooks/use-create-payroll-run-controller";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface CreatePayrollRunDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreatePayrollRunDialog({ open, onClose }: CreatePayrollRunDialogProps) {
  const { form, currentMonth } = useCreatePayrollRunController({ onClose });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo kỳ lương mới"
      description="Hệ thống sinh phiếu nháp từ mức lương của giáo viên và điểm danh trong tháng."
    >
      <form id="create-payroll-run-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField
          label="Tháng"
          htmlFor="payroll-run-period"
          hint="Tháng đang diễn ra có thể tạo để xem trước, nhưng chỉ gửi duyệt được sau khi hết tháng."
          error={form.fieldErrors.period}
        >
          <GlassInput
            id="payroll-run-period"
            type="month"
            max={currentMonth}
            value={form.values.period}
            invalid={form.fieldErrors.period !== undefined}
            onChange={(event) => form.setValue("period", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="create-payroll-run-form" loading={form.isSubmitting}>Tạo kỳ lương</GlassButton>
      </footer>
    </GlassModal>
  );
}
