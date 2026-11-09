import type { AllowanceFormRow, PayFieldSetter, PayFormValues } from "@/modules/teachers/model/teachers-forms";
import { TeacherAllowancesField } from "@/modules/teachers/ui/teacher-allowances-field";
import { PAY_TYPE, PAY_TYPE_LABEL, type PayType } from "@/shared/constants/pay-type";
import { listFieldError } from "@/shared/lib/use-zod-form";
import { FormField } from "@/shared/ui/form-field";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassSelect } from "@/shared/ui/glass-select";

export interface TeacherPayFieldsProps {
  readonly values: PayFormValues;
  readonly fieldErrors: Readonly<Record<string, string>>;
  readonly setValue: PayFieldSetter;
}

const PAY_TYPE_HINT: Record<PayType, string> = {
  [PAY_TYPE.monthly]: "Lương cố định theo ngày làm việc, có BHXH, thuế lũy tiến.",
  [PAY_TYPE.hourly]: "Số giờ lấy từ điểm danh, không BHXH, khấu trừ 10% khi từ 5 triệu đồng.",
};

/** Các ô lương dùng chung cho tạo giáo viên và sửa lương. Chỉ hiện ô thuộc hình thức đang chọn. */
export function TeacherPayFields({ values, fieldErrors, setValue }: TeacherPayFieldsProps) {
  return (
    <fieldset className="flex flex-col gap-4 rounded-2xl border border-slate-200/90 p-4">
      <legend className="px-1 text-xs font-semibold tracking-wide text-slate-600 uppercase">Lương</legend>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField label="Hình thức trả lương" htmlFor="pay-type" hint={PAY_TYPE_HINT[values.payType]}>
          <GlassSelect
            id="pay-type"
            value={values.payType}
            containerClassName="w-full"
            onChange={(event) => setValue("payType", event.target.value as PayType)}
          >
            <option value={PAY_TYPE.hourly}>{PAY_TYPE_LABEL[PAY_TYPE.hourly]}</option>
            <option value={PAY_TYPE.monthly}>{PAY_TYPE_LABEL[PAY_TYPE.monthly]}</option>
          </GlassSelect>
        </FormField>
        <FormField label="Ngày bắt đầu làm việc" htmlFor="pay-start" error={fieldErrors.workStartDate}>
          <GlassInput
            id="pay-start"
            type="date"
            value={values.workStartDate}
            invalid={fieldErrors.workStartDate !== undefined}
            onChange={(event) => setValue("workStartDate", event.target.value)}
          />
        </FormField>
      </div>

      {values.payType === PAY_TYPE.hourly ? (
        <FormField label="Đơn giá một giờ (đồng)" htmlFor="pay-hourly" error={fieldErrors.hourlyRate}>
          <GlassInput
            id="pay-hourly"
            type="number"
            min={0}
            step={1000}
            value={values.hourlyRate}
            invalid={fieldErrors.hourlyRate !== undefined}
            onChange={(event) => setValue("hourlyRate", event.target.value)}
          />
        </FormField>
      ) : (
        <MonthlyFields values={values} fieldErrors={fieldErrors} setValue={setValue} />
      )}
    </fieldset>
  );
}

function MonthlyFields({ values, fieldErrors, setValue }: TeacherPayFieldsProps) {
  return (
    <>
      <FormField label="Lương tháng (đồng)" htmlFor="pay-monthly" error={fieldErrors.monthlySalary}>
        <GlassInput
          id="pay-monthly"
          type="number"
          min={0}
          step={100000}
          value={values.monthlySalary}
          invalid={fieldErrors.monthlySalary !== undefined}
          onChange={(event) => setValue("monthlySalary", event.target.value)}
        />
      </FormField>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <FormField
          label="Hết thử việc"
          htmlFor="pay-probation"
          hint="Để trống nếu không thử việc. Ngày thử việc hưởng 85%."
          error={fieldErrors.probationEndDate}
        >
          <GlassInput
            id="pay-probation"
            type="date"
            value={values.probationEndDate}
            invalid={fieldErrors.probationEndDate !== undefined}
            onChange={(event) => setValue("probationEndDate", event.target.value)}
          />
        </FormField>
        <FormField
          label="Số người phụ thuộc"
          htmlFor="pay-dependents"
          hint="Mỗi người giảm trừ 6,2 triệu đồng khi tính thuế."
          error={fieldErrors.dependentCount}
        >
          <GlassInput
            id="pay-dependents"
            type="number"
            min={0}
            value={values.dependentCount}
            invalid={fieldErrors.dependentCount !== undefined}
            onChange={(event) => setValue("dependentCount", event.target.value)}
          />
        </FormField>
      </div>

      <TeacherAllowancesField
        rows={values.allowances}
        error={listFieldError(fieldErrors, "allowances", "Phụ cấp")}
        onChange={(rows: readonly AllowanceFormRow[]) => setValue("allowances", [...rows])}
      />
    </>
  );
}
