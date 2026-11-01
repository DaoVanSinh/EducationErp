import { FormField } from "@/shared/ui/form-field";
import { GlassInput } from "@/shared/ui/glass-input";

export interface ClassPlanValues {
  readonly maxSeats: string;
  readonly minSeats: string;
  readonly startDate: string;
  readonly endDate: string;
}

export interface ClassPlanFieldsProps {
  readonly idPrefix: string;
  readonly values: ClassPlanValues;
  readonly fieldErrors: Readonly<Record<string, string>>;
  readonly onChange: (field: keyof ClassPlanValues, value: string) => void;
  /** Ngày khai giảng sớm nhất được chọn (mở lớp mới: hôm nay). */
  readonly minStartDate?: string;
  /** Lớp đã khai giảng: ngày khai giảng và sĩ số tối thiểu không còn đổi được. */
  readonly enrollmentTermsLocked?: boolean;
  readonly minMaxSeats?: number;
  /** Gợi ý dưới ô ngày kết thúc: tự tính từ số buổi chuẩn + lịch tuần, hoặc vì sao chưa tính được. */
  readonly endDateHint?: string;
}

/**
 * Kế hoạch tuyển sinh của lớp - dùng chung cho mở lớp và sửa lớp: sĩ số tối đa/tối thiểu, ngày khai giảng,
 * ngày kết thúc dự kiến. Lớp tự khai giảng vào ngày khai giảng nếu đã đủ sĩ số tối thiểu.
 */
export function ClassPlanFields({
  idPrefix,
  values,
  fieldErrors,
  onChange,
  minStartDate,
  enrollmentTermsLocked = false,
  minMaxSeats = 1,
  endDateHint = "Không bắt buộc.",
}: ClassPlanFieldsProps) {
  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
      <FormField label="Sĩ số tối đa" htmlFor={`${idPrefix}-max-seats`} error={fieldErrors.maxSeats}>
        <GlassInput
          id={`${idPrefix}-max-seats`}
          type="number"
          min={minMaxSeats}
          value={values.maxSeats}
          invalid={fieldErrors.maxSeats !== undefined}
          onChange={(event) => onChange("maxSeats", event.target.value)}
        />
      </FormField>
      <FormField
        label="Sĩ số tối thiểu để khai giảng"
        htmlFor={`${idPrefix}-min-seats`}
        error={fieldErrors.minSeats}
        hint={enrollmentTermsLocked ? "Lớp đã khai giảng." : "Chưa đủ số này thì lớp chưa bắt đầu học."}
      >
        <GlassInput
          id={`${idPrefix}-min-seats`}
          type="number"
          min={1}
          disabled={enrollmentTermsLocked}
          value={values.minSeats}
          invalid={fieldErrors.minSeats !== undefined}
          onChange={(event) => onChange("minSeats", event.target.value)}
        />
      </FormField>
      <FormField label="Ngày khai giảng" htmlFor={`${idPrefix}-start-date`} error={fieldErrors.startDate}>
        <GlassInput
          id={`${idPrefix}-start-date`}
          type="date"
          min={minStartDate}
          disabled={enrollmentTermsLocked}
          value={values.startDate}
          invalid={fieldErrors.startDate !== undefined}
          onChange={(event) => onChange("startDate", event.target.value)}
        />
      </FormField>
      <FormField
        label="Ngày kết thúc dự kiến"
        htmlFor={`${idPrefix}-end-date`}
        error={fieldErrors.endDate}
        hint={endDateHint}
      >
        <GlassInput
          id={`${idPrefix}-end-date`}
          type="date"
          min={values.startDate}
          value={values.endDate}
          invalid={fieldErrors.endDate !== undefined}
          onChange={(event) => onChange("endDate", event.target.value)}
        />
      </FormField>
    </div>
  );
}
