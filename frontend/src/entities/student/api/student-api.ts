import {
  studentProfileSummarySchema,
  type CreateStudentProfilePayload,
  type UpdateStudentProfilePayload,
} from "@/entities/student/model/student-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const studentProfilePageSchema = pageResponseSchema(studentProfileSummarySchema);
const createdStudentProfileIdSchema = z.string().uuid();

export const studentApi = {
  async listProfiles(page: number, size: number) {
    return studentProfilePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.students.profiles, { page, size }),
    );
  },

  async createProfile(payload: CreateStudentProfilePayload): Promise<string> {
    return createdStudentProfileIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.students.profiles, payload),
    );
  },

  async updateProfile(profileId: string, payload: UpdateStudentProfilePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.students.profile(profileId), payload);
  },
} as const;
