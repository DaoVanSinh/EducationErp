import type { Payslip } from "@/entities/payroll";
import { useUpdatePayslip } from "@/modules/payroll/api/use-payroll-run-mutations";
import { adjustPayslipFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";

export interface AdjustPayslipControllerOptions {
  readonly runId: string;
  readonly payslip: Payslip;
  readonly onClose: () => void;
}

export function useAdjustPayslipController({ runId, payslip, onClose }: AdjustPayslipControllerOptions) {
  const updatePayslip = useUpdatePayslip(runId);

  return useZodForm({
    schema: adjustPayslipFormSchema,
    initialValues: { adjustment: String(payslip.adjustment), note: payslip.note ?? "" },
    onSubmit: async (payload) => {
      await updatePayslip.mutateAsync({ payslipId: payslip.id, payload });
      onClose();
    },
  });
}
