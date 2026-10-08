import { ENROLLMENT_STATUS, type EnrollmentSummary } from "@/entities/enrollment";
import { useCreateInvoice } from "@/modules/billing/api/use-billing-mutations";
import { createInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateInvoiceDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly enrollments: readonly EnrollmentSummary[];
}

export function CreateInvoiceDialog({ open, onClose, enrollments }: CreateInvoiceDialogProps) {
  const createInvoice = useCreateInvoice();

  const form = useZodForm({
    schema: createInvoiceFormSchema,
    initialValues: { enrollmentId: "", amount: "", dueDate: "" },
    onSubmit: async (values) => {
      await createInvoice.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo đợt thu học phí"
      description="Tối đa 3 đợt cho mỗi ghi danh; tổng các đợt không vượt học phí khoá học."
    >
      <form id="create-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ghi danh" htmlFor="invoice-enrollment" hint="Chỉ ghi danh đang học phát hành được." error={form.fieldErrors.enrollmentId}>
          <GlassSelect
            id="invoice-enrollment"
            className="w-full"
            containerClassName="w-full"
            value={form.values.enrollmentId}
            invalid={form.fieldErrors.enrollmentId !== undefined}
            onChange={(event) => form.setValue("enrollmentId", event.target.value)}
          >
            <option value="">Chọn ghi danh</option>
            {enrollments
              .filter((enrollment) => enrollment.status === ENROLLMENT_STATUS.active)
              .map((enrollment) => (
                <option key={enrollment.id} value={enrollment.id}>
                  {enrollment.id.slice(0, 8)} — HV {enrollment.studentProfileId.slice(0, 8)}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField label="Số tiền đợt này (VND)" htmlFor="invoice-amount" error={form.fieldErrors.amount}>
          <GlassInput
            id="invoice-amount"
            type="number"
            min={1}
            step={1000}
            value={form.values.amount}
            invalid={form.fieldErrors.amount !== undefined}
            onChange={(event) => form.setValue("amount", event.target.value)}
          />
        </FormField>

        <FormField label="Hạn thanh toán" htmlFor="invoice-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="invoice-due-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-invoice-form" loading={form.isSubmitting}>
          Tạo đợt thu
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
