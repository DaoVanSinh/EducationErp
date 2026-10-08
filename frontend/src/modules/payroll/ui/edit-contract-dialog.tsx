import type { ContractSummary } from "@/entities/payroll";
import { useUpdateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { updateContractFormSchema } from "@/modules/payroll/model/payroll-forms";
import { AllowancesFieldList } from "@/modules/payroll/ui/allowances-field-list";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { useState } from "react";

export interface EditContractDialogProps {
  readonly contract: ContractSummary;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function EditContractDialog({ contract, open, onClose }: EditContractDialogProps) {
  const updateContract = useUpdateContract(contract.id);
  const [file, setFile] = useState<File | null>(null);

  const form = useZodForm({
    schema: updateContractFormSchema,
    initialValues: {
      baseSalary: contract.baseSalary === null ? "" : String(contract.baseSalary),
      hourlyRate: contract.hourlyRate === null ? "" : String(contract.hourlyRate),
      probationEndDate: contract.probationEndDate ?? "",
      allowances: contract.allowances,
    },
    onSubmit: async (values) => {
      await updateContract.mutateAsync({ payload: values, file });
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Cập nhật hợp đồng" description={contract.accountFullName ?? contract.accountEmail ?? ""}>
      <form id="edit-contract-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Lương cố định (OFFICIAL)" htmlFor="edit-contract-base-salary">
          <GlassInput id="edit-contract-base-salary" type="number" value={form.values.baseSalary}
            onChange={(event) => form.setValue("baseSalary", event.target.value)} />
        </FormField>

        <FormField label="Đơn giá/giờ (CTV)" htmlFor="edit-contract-hourly-rate">
          <GlassInput id="edit-contract-hourly-rate" type="number" value={form.values.hourlyRate}
            onChange={(event) => form.setValue("hourlyRate", event.target.value)} />
        </FormField>

        <FormField label="Ngày kết thúc thử việc" htmlFor="edit-contract-probation-end">
          <GlassInput id="edit-contract-probation-end" type="date" value={form.values.probationEndDate}
            onChange={(event) => form.setValue("probationEndDate", event.target.value)} />
        </FormField>

        <AllowancesFieldList allowances={form.values.allowances} onChange={(a) => form.setValue("allowances", [...a])} />

        <FormField label="File hợp đồng (PDF)" htmlFor="edit-contract-file" hint="Để trống nếu không đổi file.">
          <input id="edit-contract-file" type="file" accept="application/pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="edit-contract-form" loading={form.isSubmitting}>Lưu</GlassButton>
      </footer>
    </GlassModal>
  );
}
