import {
  classSummarySchema,
  type CreateClassPayload,
  type UpdateClassPayload,
} from "@/entities/class/model/class-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const classPageSchema = pageResponseSchema(classSummarySchema);
const createdClassIdSchema = z.string().uuid();

export const classApi = {
  async listClasses(page: number, size: number) {
    return classPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.courses.classes, { page, size }),
    );
  },

  async createClass(payload: CreateClassPayload): Promise<string> {
    return createdClassIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.courses.classes, payload),
    );
  },

  async updateClass(classId: string, payload: UpdateClassPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.courses.class(classId), payload);
  },
} as const;
