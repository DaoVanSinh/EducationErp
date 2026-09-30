import { z } from "zod";

export const branchSummarySchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  address: z.string().nullable(),
  active: z.boolean(),
});

export type BranchSummary = z.infer<typeof branchSummarySchema>;

export interface CreateBranchPayload {
  readonly code: string;
  readonly name: string;
  readonly address: string | null;
}

export interface UpdateBranchPayload {
  readonly name: string;
  readonly address: string | null;
  readonly active: boolean;
}
