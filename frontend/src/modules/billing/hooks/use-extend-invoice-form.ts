import { useExtendInvoiceDue } from "@/modules/billing/api/use-billing-mutations";
import { extendInvoiceFormSchema } from "@/modules/billing/model/billing-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function useExtendInvoiceForm(invoiceId: string, onClose: () => void) {
  const extendDue = useExtendInvoiceDue(invoiceId);
  return useZodForm({
    schema: extendInvoiceFormSchema,
    initialValues: { dueDate: "", reason: "" },
    onSubmit: async (values) => {
      await extendDue.mutateAsync(values);
      onClose();
    },
  });
}
