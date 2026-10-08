import { z } from "zod";

export const createEnrollmentFormSchema = z.object({
  studentProfileId: z.string().uuid("Chọn học viên"),
  classId: z.string().uuid("Chọn lớp học"),
});
