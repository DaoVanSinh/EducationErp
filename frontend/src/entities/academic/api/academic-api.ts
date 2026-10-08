import {
  classProgressSchema,
  gradebookSchema,
  type RecordGradePayload,
} from "@/entities/academic/model/academic-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const academicApi = {
  async getGradebook(classId: string) {
    return gradebookSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.academic.classGradebook(classId)),
    );
  },

  async getClassProgress(classId: string) {
    return classProgressSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.academic.classProgress(classId)),
    );
  },

  async recordGrade(payload: RecordGradePayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.academic.grades, payload);
  },

  /** getBlob đi qua lượt tự refresh khi access token hết hạn và đọc luôn tên file từ
   * Content-Disposition - cùng đường mà tải file hợp đồng đã dùng. */
  async downloadGradebook(classId: string): Promise<{ blob: Blob; fileName: string | null }> {
    return apiClient.getBlob(API_ROUTE.academic.classGradebookExport(classId));
  },

  async downloadClassProgress(classId: string): Promise<{ blob: Blob; fileName: string | null }> {
    return apiClient.getBlob(API_ROUTE.academic.classProgressExport(classId));
  },
} as const;
