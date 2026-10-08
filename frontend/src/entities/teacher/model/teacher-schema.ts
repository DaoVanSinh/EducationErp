import { z } from "zod";

export const teacherProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  subjects: z.array(z.string()),
  phone: z.string().nullable(),
  bio: z.string().nullable(),
  active: z.boolean(),
});

export type TeacherProfileSummary = z.infer<typeof teacherProfileSummarySchema>;

export interface CreateTeacherProfilePayload {
  readonly accountId: string;
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
}

export interface UpdateTeacherProfilePayload {
  readonly subjects: readonly string[];
  readonly phone: string | null;
  readonly bio: string | null;
  readonly active: boolean;
}
