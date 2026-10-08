import { CONTRACT_TYPE, CONTRACT_TYPE_LABEL } from "@/entities/payroll";
import { useCreateContract } from "@/modules/payroll/api/use-contracts-mutations";
import { createContractFormSchema } from "@/modules/payroll/model/payroll-forms";
import { AllowancesFieldList } from "@/modules/payroll/ui/allowances-field-list";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";
import { useState } from "react";

export interface CreateContractDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateContractDialog({ open, onClose }: CreateContractDialogProps) {
  const createContract = useCreateContract();
  const [file, setFile] = useState<File | null>(null);

  const form = useZodForm({
    schema: createContractFormSchema,
    initialValues: {
      accountId: "",
      contractType: CONTRACT_TYPE.official,
      baseSalary: "",
      hourlyRate: "",
      probationStartDate: "",
      probationEndDate: "",
      startDate: "",
      allowances: [],
    },
    onSubmit: async (values) => {
      await createContract.mutateAsync({ payload: values, file });
      form.reset();
      setFile(null);
      onClose();
    },
  });

  return (
    <GlassModal open={open} onClose={onClose} title="Tạo hợp đồng lao động"
      description="Áp dụng cho mọi nhân viên có hợp đồng, không riêng giáo viên.">
      <form id="create-contract-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Mã tài khoản nhân viên (UUID)" htmlFor="contract-account-id" error={form.fieldErrors.accountId}>
          <GlassInput id="contract-account-id" value={form.values.accountId}
            onChange={(event) => form.setValue("accountId", event.target.value)} />
        </FormField>

        <FormField label="Loại hợp đồng" htmlFor="contract-type">
          <GlassSelect id="contract-type" value={form.values.contractType}
            onChange={(event) => form.setValue("contractType", event.target.value as typeof form.values.contractType)}>
            <option value={CONTRACT_TYPE.official}>{CONTRACT_TYPE_LABEL[CONTRACT_TYPE.official]}</option>
            <option value={CONTRACT_TYPE.collaborator}>{CONTRACT_TYPE_LABEL[CONTRACT_TYPE.collaborator]}</option>
          </GlassSelect>
        </FormField>

        <FormField label="Lương cố định (OFFICIAL)" htmlFor="contract-base-salary" hint="Bắt buộc với hợp đồng chính thức.">
          <GlassInput id="contract-base-salary" type="number" value={form.values.baseSalary}
            onChange={(event) => form.setValue("baseSalary", event.target.value)} />
        </FormField>

        <FormField label="Đơn giá/giờ (CTV)" htmlFor="contract-hourly-rate" hint="Bắt buộc với hợp đồng CTV/thời vụ.">
          <GlassInput id="contract-hourly-rate" type="number" value={form.values.hourlyRate}
            onChange={(event) => form.setValue("hourlyRate", event.target.value)} />
        </FormField>

        <FormField label="Ngày bắt đầu thử việc" htmlFor="contract-probation-start" hint="Chỉ áp dụng HĐLĐ chính thức.">
          <GlassInput id="contract-probation-start" type="date" value={form.values.probationStartDate}
            onChange={(event) => form.setValue("probationStartDate", event.target.value)} />
        </FormField>

        <FormField label="Ngày kết thúc thử việc" htmlFor="contract-probation-end">
          <GlassInput id="contract-probation-end" type="date" value={form.values.probationEndDate}
            onChange={(event) => form.setValue("probationEndDate", event.target.value)} />
        </FormField>

        <FormField label="Ngày bắt đầu hợp đồng" htmlFor="contract-start-date" error={form.fieldErrors.startDate}>
          <GlassInput id="contract-start-date" type="date" value={form.values.startDate}
            onChange={(event) => form.setValue("startDate", event.target.value)} />
        </FormField>

        <AllowancesFieldList allowances={form.values.allowances} onChange={(a) => form.setValue("allowances", [...a])} />

        <FormField label="File hợp đồng (PDF)" htmlFor="contract-file" hint="Không bắt buộc.">
          <input id="contract-file" type="file" accept="application/pdf"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>Huỷ</GlassButton>
        <GlassButton type="submit" form="create-contract-form" loading={form.isSubmitting}>Tạo hợp đồng</GlassButton>
      </footer>
    </GlassModal>
  );
}
