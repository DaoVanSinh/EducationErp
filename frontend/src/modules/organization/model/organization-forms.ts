import { z } from "zod";

const addressField = z
  .string()
  .trim()
  .transform((value) => (value.length === 0 ? null : value));

/** Mã chi nhánh bất biến sau khi tạo - backend không cho sửa, nên form tạo mới là nơi duy nhất nhập nó. */
const BRANCH_CODE_PATTERN = /^[A-Z][A-Z0-9_]*$/;

export const createBranchFormSchema = z.object({
  code: z
    .string()
    .min(1, "Nhập mã chi nhánh")
    .regex(BRANCH_CODE_PATTERN, "Mã chỉ gồm chữ in hoa, số và dấu gạch dưới, ví dụ HCM01"),
  name: z.string().min(1, "Nhập tên chi nhánh"),
  address: addressField,
});

export const editBranchFormSchema = z.object({
  name: z.string().min(1, "Nhập tên chi nhánh"),
  address: addressField,
  active: z.boolean(),
});
