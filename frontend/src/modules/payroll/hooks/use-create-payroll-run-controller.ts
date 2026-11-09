import { useCreatePayrollRun } from "@/modules/payroll/api/use-payroll-run-mutations";
import { createPayrollRunFormSchema } from "@/modules/payroll/model/payroll-run-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useMemo } from "react";

function monthValue(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
}

/** Mặc định tháng trước, vì lương thường chốt sau khi tháng đã hết. Tháng lớn nhất chọn được là tháng hiện tại. */
export function useCreatePayrollRunController({ onClose }: { readonly onClose: () => void }) {
  const createPayrollRun = useCreatePayrollRun();
  const { currentMonth, previousMonth } = useMemo(() => {
    const now = new Date();
    return {
      currentMonth: monthValue(now),
      previousMonth: monthValue(new Date(now.getFullYear(), now.getMonth() - 1, 1)),
    };
  }, []);

  const form = useZodForm({
    schema: createPayrollRunFormSchema,
    initialValues: { period: previousMonth },
    onSubmit: async (values) => {
      await createPayrollRun.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  return { form, currentMonth };
}
