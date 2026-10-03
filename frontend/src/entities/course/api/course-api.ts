import {
  courseSummarySchema,
  type CreateCoursePayload,
  type UpdateCoursePayload,
} from "@/entities/course/model/course-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const coursePageSchema = pageResponseSchema(courseSummarySchema);
const createdCourseIdSchema = z.string().uuid();

export const courseApi = {
  async listCourses(page: number, size: number) {
    return coursePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.courses.courses, { page, size }),
    );
  },

  async createCourse(payload: CreateCoursePayload): Promise<string> {
    return createdCourseIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.courses.courses, payload),
    );
  },

  async updateCourse(courseId: string, payload: UpdateCoursePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.courses.course(courseId), payload);
  },
} as const;
