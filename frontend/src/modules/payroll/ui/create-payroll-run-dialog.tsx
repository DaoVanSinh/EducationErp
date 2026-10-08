import { useCreatePayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { createPayrollRunFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
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
  const createPayrollRun = useCreatePayrollRun();

  const form = useZodForm({
    schema: createPayrollRunFormSchema,
    initialValues: { year: String(new Date().getFullYear()), month: String(new Date().getMonth() + 1) },
    onSubmit: async (values) => {
      await createPayrollRun.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo kỳ lương mới"
      description="Tự sinh phiếu lương nháp cho mọi hợp đồng đang hiệu lực.">
      <form id="create-payroll-run-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Năm" htmlFor="payroll-run-year" error={form.fieldErrors.year}>
          <GlassInput id="payroll-run-year" type="number" value={form.values.year}
            onChange={(event) => form.setValue("year", event.target.value)} />
        </FormField>

        <FormField label="Tháng" htmlFor="payroll-run-month" error={form.fieldErrors.month}>
          <GlassInput id="payroll-run-month" type="number" min={1} max={12} value={form.values.month}
            onChange={(event) => form.setValue("month", event.target.value)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="create-payroll-run-form" loading={form.isSubmitting}>Tạo kỳ lương</GlassButton>
      </footer>
    </GlassModal>
  );
}
