import { z } from "zod";

export const courseSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  active: z.boolean(),
});

export type CourseSummary = z.infer<typeof courseSummarySchema>;

export interface CreateCoursePayload {
  readonly code: string;
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
}

export interface UpdateCoursePayload {
  readonly name: string;
  readonly description: string | null;
  readonly standardSessionCount: number | null;
  readonly active: boolean;
}
