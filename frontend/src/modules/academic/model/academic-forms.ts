import { z } from "zod";

/**
 * Trần của điểm là `maxScore` của RIÊNG cột đang nhập, nên nó được truyền vào lúc dựng schema thay
 * vì là một hằng trong file. Backend kiểm lại bằng ACADEMIC_INVALID_SCORE - luật ở đây chỉ để người
 * nhập thấy lỗi trước khi gửi.
 */
export function createRecordGradeFormSchema(maxScore: number) {
  return z.object({
    score: z
      .string()
      .min(1, "Nhập điểm")
      .transform((value) => Number(value))
      .refine((value) => Number.isFinite(value), "Điểm phải là một số")
      .refine((value) => value >= 0, "Điểm không được âm")
      .refine((value) => value <= maxScore, `Điểm không được vượt thang ${maxScore}`),
  });
}
