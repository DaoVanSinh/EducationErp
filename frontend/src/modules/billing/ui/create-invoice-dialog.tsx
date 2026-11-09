import { enrollmentFullLabel, type EnrollmentSummary } from "@/entities/enrollment";
import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { useCreateInvoice } from "@/modules/billing/api/use-billing-mutations";
import { createInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface CreateInvoiceDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly enrollments: readonly EnrollmentSummary[];
}

/** Hoá đơn tự lập khi ghi danh; ở đây chỉ dùng khi hoá đơn đã bị huỷ, khoá mới có giá, hoặc dữ liệu chia đợt cũ còn dở. */
export function CreateInvoiceDialog({ open, onClose, enrollments }: CreateInvoiceDialogProps) {
  const createInvoice = useCreateInvoice();

  const form = useZodForm({
    schema: createInvoiceFormSchema,
    initialValues: { enrollmentId: "" },
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
      title="Lập lại hoá đơn học phí"
      description="Hệ thống tính phần học phí chưa có hoá đơn, thu đủ một lần, hạn là ngày thứ 7 từ ngày khai giảng."
    >
      <form id="create-invoice-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Ghi danh" htmlFor="invoice-enrollment" hint="Chỉ ghi danh đang học." error={form.fieldErrors.enrollmentId}>
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
                  {enrollmentFullLabel(enrollment)}
                </option>
              ))}
          </GlassSelect>
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-invoice-form" loading={form.isSubmitting}>
          Lập hoá đơn
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
