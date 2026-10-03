import { z } from "zod";

export const studentProfileSummarySchema = z.object({
  id: z.string().uuid(),
  accountId: z.string().uuid(),
  fullName: z.string().nullable(),
  email: z.string().nullable(),
  dateOfBirth: z.string().nullable(),
  phone: z.string().nullable(),
  sourceChannel: z.string().nullable(),
  active: z.boolean(),
});

export type StudentProfileSummary = z.infer<typeof studentProfileSummarySchema>;

export interface CreateStudentProfilePayload {
  readonly accountId: string;
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
}

export interface UpdateStudentProfilePayload {
  readonly dateOfBirth: string | null;
  readonly phone: string | null;
  readonly sourceChannel: string | null;
  readonly active: boolean;
}
