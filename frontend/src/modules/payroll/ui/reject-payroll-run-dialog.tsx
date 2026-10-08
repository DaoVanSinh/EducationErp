import { useRejectPayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { rejectPayrollRunFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";

export interface RejectPayrollRunDialogProps {
  readonly runId: string;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function RejectPayrollRunDialog({ runId, open, onClose }: RejectPayrollRunDialogProps) {
  const rejectPayrollRun = useRejectPayrollRun(runId);

  const form = useZodForm({
    schema: rejectPayrollRunFormSchema,
    initialValues: { reason: "" },
    onSubmit: async (values) => {
      await rejectPayrollRun.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Từ chối kỳ lương" description="Kỳ lương về lại trạng thái Nháp.">
      <form id="reject-payroll-run-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}
        <FormField label="Lý do từ chối" htmlFor="reject-reason" error={form.fieldErrors.reason}>
          <GlassInput id="reject-reason" value={form.values.reason}
            onChange={(event) => form.setValue("reason", event.target.value)} />
        </FormField>
      </form>
      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="reject-payroll-run-form" loading={form.isSubmitting}>Từ chối</GlassButton>
      </footer>
    </GlassModal>
  );
}
