import {
  attemptResultSchema,
  classAssignmentsSchema,
  classMiniTasksSchema,
  createdResponseSchema,
  myAssessmentsSchema,
  practiceMiniTaskSchema,
  type CreateAssignmentPayload,
  type CreateMiniTaskPayload,
  type ResultEntryPayload,
  type UpdateAssignmentPayload,
  type UpdateMiniTaskPayload,
} from "@/entities/assessment/model/assessment-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";

export const assessmentApi = {
  async getClassAssignments(classId: string) {
    return classAssignmentsSchema.parse(await apiClient.get<unknown>(API_ROUTE.assessment.classAssignments(classId)));
  },

  async createAssignment(payload: CreateAssignmentPayload) {
    return createdResponseSchema.parse(await apiClient.post<unknown>(API_ROUTE.assessment.assignments, payload));
  },

  async updateAssignment(assignmentId: string, payload: UpdateAssignmentPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.assessment.assignment(assignmentId), payload);
  },

  async recordResults(assignmentId: string, entries: readonly ResultEntryPayload[]): Promise<void> {
    await apiClient.post<void>(API_ROUTE.assessment.assignmentResults(assignmentId), { entries });
  },

  async getClassMiniTasks(classId: string) {
    return classMiniTasksSchema.parse(await apiClient.get<unknown>(API_ROUTE.assessment.classMiniTasks(classId)));
  },

  async createMiniTask(payload: CreateMiniTaskPayload) {
    return createdResponseSchema.parse(await apiClient.post<unknown>(API_ROUTE.assessment.miniTasks, payload));
  },

  async updateMiniTask(miniTaskId: string, payload: UpdateMiniTaskPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.assessment.miniTask(miniTaskId), payload);
  },

  /** Cổng học tập - backend tự lọc theo học viên đang đăng nhập. */
  async listMyAssessments() {
    return myAssessmentsSchema.parse(await apiClient.get<unknown>(API_ROUTE.assessment.myClasses));
  },

  async getPracticeMiniTask(miniTaskId: string) {
    return practiceMiniTaskSchema.parse(await apiClient.get<unknown>(API_ROUTE.assessment.myMiniTask(miniTaskId)));
  },

  /** `answers[i]` là phương án chọn cho câu i (null = bỏ trống). */
  async submitAttempt(miniTaskId: string, answers: readonly (number | null)[]) {
    return attemptResultSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.assessment.myMiniTaskAttempts(miniTaskId), { answers }),
    );
  },
} as const;
