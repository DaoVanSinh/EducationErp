import {
  teacherProfileSummarySchema,
  type CreateTeacherProfilePayload,
  type UpdateTeacherProfilePayload,
} from "@/entities/teacher/model/teacher-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const teacherProfilePageSchema = pageResponseSchema(teacherProfileSummarySchema);
const createdTeacherProfileIdSchema = z.string().uuid();

export const teacherApi = {
  async listProfiles(page: number, size: number) {
    return teacherProfilePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.teachers.profiles, { page, size }),
    );
  },

  async createProfile(payload: CreateTeacherProfilePayload): Promise<string> {
    return createdTeacherProfileIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.teachers.profiles, payload),
    );
  },

  async updateProfile(profileId: string, payload: UpdateTeacherProfilePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.teachers.profile(profileId), payload);
  },
} as const;
