import { PAYSLIP_PAYMENT_METHOD } from "@/entities/payroll";
import { useMarkPayslipPaid, useUndoPayslipPayment } from "@/modules/payroll/api/use-payroll-run-mutations";
import { markPayslipPaidFormSchema, undoPayslipPaymentFormSchema } from "@/modules/payroll/model/branch-payslip-forms";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useZodForm } from "@/shared/lib/use-zod-form";

export function usePayPayslipForm(payslipId: string, onClose: () => void) {
  const markPaid = useMarkPayslipPaid(payslipId);
  return useZodForm({
    schema: markPayslipPaidFormSchema,
    initialValues: { paidOn: todayLocalIsoDate(), method: PAYSLIP_PAYMENT_METHOD.bankTransfer, note: "" },
    onSubmit: async (values) => {
      await markPaid.mutateAsync(values);
      onClose();
    },
  });
}

export function useUndoPayslipPaymentForm(payslipId: string, onClose: () => void) {
  const undoPayment = useUndoPayslipPayment(payslipId);
  return useZodForm({
    schema: undoPayslipPaymentFormSchema,
    initialValues: { reason: "" },
    onSubmit: async (values) => {
      await undoPayment.mutateAsync(values);
      onClose();
    },
  });
}
