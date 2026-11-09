import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { PAY_TYPE, type PayType } from "@/shared/constants/pay-type";
import type { TeacherPay, TeacherPayTermsPayload } from "@/entities/teacher";
import { z } from "zod";

const optionalText = z
  .string()
  .trim()
  .transform((value) => (value.length === 0 ? null : value));

const subjectList = z
  .string()
  .trim()
  .transform((value) =>
    value.length === 0
      ? []
      : value
          .split(",")
          .map((subject) => subject.trim())
          .filter((subject) => subject.length > 0),
  );

/** Ô tiền là chuỗi trong form; để trống hợp lệ ở đây, bắt buộc hay không tuỳ hình thức trả lương. */
const moneyText = z
  .string()
  .trim()
  .refine((value) => value.length === 0 || Number(value) > 0, "Nhập số lớn hơn 0");

const allowanceRow = z.object({
  name: z.string().trim().min(1, "Nhập tên phụ cấp"),
  amount: z.string().trim().refine((value) => Number(value) > 0, "Nhập số tiền lớn hơn 0"),
});

export type AllowanceFormRow = z.input<typeof allowanceRow>;

const payFields = {
  payType: z.enum([PAY_TYPE.monthly, PAY_TYPE.hourly]),
  monthlySalary: moneyText,
  hourlyRate: moneyText,
  workStartDate: z.string().min(1, "Chọn ngày bắt đầu làm việc"),
  probationEndDate: z.string(),
  dependentCount: z.string().trim().refine((value) => Number.isInteger(Number(value)) && Number(value) >= 0, "Nhập số nguyên từ 0"),
  allowances: z.array(allowanceRow),
};

/** Giá trị các ô lương trong form, dùng chung cho tạo giáo viên và sửa lương. */
export interface PayFormValues {
  readonly payType: PayType;
  readonly monthlySalary: string;
  readonly hourlyRate: string;
  readonly workStartDate: string;
  readonly probationEndDate: string;
  readonly dependentCount: string;
  readonly allowances: AllowanceFormRow[];
}

/** Setter của riêng các ô lương - TeacherPayFields không cần biết form bao ngoài còn những ô nào. */
export type PayFieldSetter = <TField extends keyof PayFormValues>(field: TField, value: PayFormValues[TField]) => void;

/**
 * TypeScript không suy ra được rằng setter của một form chứa đủ các ô lương cũng dùng được cho riêng các ô đó, vì
 * cả hai đều generic theo tên ô. Ép kiểu ở đúng một chỗ này thay vì rải khắp các dialog.
 */
export function toPayFieldSetter(setValue: (field: never, value: never) => void): PayFieldSetter {
  return setValue as unknown as PayFieldSetter;
}

export const EMPTY_PAY_VALUES: PayFormValues = {
  payType: PAY_TYPE.hourly,
  monthlySalary: "",
  hourlyRate: "",
  workStartDate: todayLocalIsoDate(),
  probationEndDate: "",
  dependentCount: "0",
  allowances: [],
};

export function toPayFormValues(pay: TeacherPay): PayFormValues {
  return {
    payType: pay.payType ?? PAY_TYPE.hourly,
    monthlySalary: pay.monthlySalary === null ? "" : String(pay.monthlySalary),
    hourlyRate: pay.hourlyRate === null ? "" : String(pay.hourlyRate),
    workStartDate: pay.workStartDate ?? todayLocalIsoDate(),
    probationEndDate: pay.probationEndDate ?? "",
    dependentCount: String(pay.dependentCount),
    allowances: pay.allowances.map((allowance) => ({ name: allowance.name, amount: String(allowance.amount) })),
  };
}

type PayFieldValues = {
  readonly payType: PayType;
  readonly monthlySalary: string;
  readonly hourlyRate: string;
  readonly workStartDate: string;
  readonly probationEndDate: string;
};

/** Ô bắt buộc tuỳ hình thức trả lương - backend kiểm tra lại bằng TeacherPayRules. */
function refinePayFields(values: PayFieldValues, context: z.RefinementCtx): void {
  if (values.payType === PAY_TYPE.hourly) {
    if (values.hourlyRate.length === 0) {
      context.addIssue({ code: "custom", path: ["hourlyRate"], message: "Nhập đơn giá một giờ" });
    }
    return;
  }
  if (values.monthlySalary.length === 0) {
    context.addIssue({ code: "custom", path: ["monthlySalary"], message: "Nhập lương tháng" });
  }
  if (values.probationEndDate.length > 0 && values.probationEndDate < values.workStartDate) {
    context.addIssue({
      code: "custom",
      path: ["probationEndDate"],
      message: "Ngày hết thử việc phải sau ngày bắt đầu làm việc",
    });
  }
}

/** Ô của hình thức không chọn bị bỏ đi, để đổi qua lại giữa hai hình thức không gửi dữ liệu thừa lên backend. */
function toPayTerms(values: z.output<z.ZodObject<typeof payFields>>): TeacherPayTermsPayload {
  const shared = { payType: values.payType, workStartDate: values.workStartDate };
  if (values.payType === PAY_TYPE.hourly) {
    return {
      ...shared,
      monthlySalary: null,
      hourlyRate: Number(values.hourlyRate),
      probationEndDate: null,
      dependentCount: 0,
      allowances: [],
    };
  }
  return {
    ...shared,
    monthlySalary: Number(values.monthlySalary),
    hourlyRate: null,
    probationEndDate: values.probationEndDate.length === 0 ? null : values.probationEndDate,
    dependentCount: Number(values.dependentCount),
    allowances: values.allowances.map((row) => ({ name: row.name, amount: Number(row.amount) })),
  };
}

/** Tạo giáo viên một bước: tài khoản đăng nhập, hồ sơ và điều khoản lương. */
export const createTeacherFormSchema = z
  .object({
    email: z.string().trim().email("Email không hợp lệ"),
    fullName: z.string().trim().min(1, "Nhập họ tên giáo viên"),
    homeBranchId: z.string().min(1, "Chọn chi nhánh chủ quản"),
    subjects: subjectList.refine((subjects) => subjects.length > 0, "Nhập ít nhất một môn dạy"),
    phone: optionalText,
    bio: optionalText,
    ...payFields,
  })
  .superRefine(refinePayFields)
  .transform((values) => ({
    email: values.email,
    fullName: values.fullName,
    homeBranchId: values.homeBranchId,
    subjects: values.subjects,
    phone: values.phone,
    bio: values.bio,
    payTerms: toPayTerms(values),
  }));

/** Sửa lương của một giáo viên đã có. */
export const teacherPayFormSchema = z.object(payFields).superRefine(refinePayFields).transform(toPayTerms);

export const updateTeacherFormSchema = z.object({
  subjects: subjectList,
  phone: optionalText,
  bio: optionalText,
});

export const leaveTeacherFormSchema = z.object({
  lastWorkDate: z.string().min(1, "Chọn ngày làm việc cuối"),
});
