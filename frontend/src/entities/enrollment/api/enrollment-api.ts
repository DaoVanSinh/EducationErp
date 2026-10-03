import {
  enrollmentSummarySchema,
  type CreateEnrollmentPayload,
} from "@/entities/enrollment/model/enrollment-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";

const enrollmentPageSchema = pageResponseSchema(enrollmentSummarySchema);

export const enrollmentApi = {
  async listEnrollments(page: number, size: number, studentProfileId?: string, classId?: string) {
    return enrollmentPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.enrollment.enrollments, {
        page,
        size,
        studentProfileId,
        classId,
      }),
    );
  },

  async getEnrollment(enrollmentId: string) {
    return enrollmentSummarySchema.parse(
      await apiClient.get<unknown>(API_ROUTE.enrollment.enrollment(enrollmentId)),
    );
  },

  async createEnrollment(payload: CreateEnrollmentPayload) {
    return enrollmentSummarySchema.parse(
      await apiClient.post<unknown>(API_ROUTE.enrollment.enrollments, payload),
    );
  },

  async withdrawEnrollment(enrollmentId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.enrollment.enrollmentWithdraw(enrollmentId));
  },

  async completeEnrollment(enrollmentId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.enrollment.enrollmentComplete(enrollmentId));
  },
} as const;
