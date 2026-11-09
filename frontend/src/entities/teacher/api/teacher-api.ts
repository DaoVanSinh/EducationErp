import {
  teacherBranchChangeSchema,
  teacherPaySchema,
  teacherProfileSummarySchema,
  type CreateTeacherPayload,
  type LeaveTeacherPayload,
  type TeacherPayTermsPayload,
  type UpdateTeacherProfilePayload,
} from "@/entities/teacher/model/teacher-schema";
import { apiClient } from "@/shared/api/api-client";
import { pageResponseSchema } from "@/shared/api/schemas";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const teacherProfilePageSchema = pageResponseSchema(teacherProfileSummarySchema);
const createdTeacherProfileIdSchema = z.string().uuid();
const teacherBranchHistorySchema = z.array(teacherBranchChangeSchema);

export const teacherApi = {
  async listProfiles(page: number, size: number, branchId?: string, query?: string) {
    return teacherProfilePageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.teachers.profiles, { page, size, branchId, query }),
    );
  },

  async getProfile(profileId: string) {
    return teacherProfileSummarySchema.parse(
      await apiClient.get<unknown>(API_ROUTE.teachers.profile(profileId)),
    );
  },

  async createProfile(payload: CreateTeacherPayload): Promise<string> {
    return createdTeacherProfileIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.teachers.profiles, payload),
    );
  },

  async updateProfile(profileId: string, payload: UpdateTeacherProfilePayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.teachers.profile(profileId), payload);
  },

  /** Cho nghỉ: dừng lương từ sau ngày này, bị chặn khi còn lớp mở. Tài khoản vẫn đăng nhập xem dữ liệu của mình. */
  async leave(profileId: string, payload: LeaveTeacherPayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.teachers.profileLeave(profileId), payload);
  },

  async getBranchHistory(profileId: string) {
    return teacherBranchHistorySchema.parse(
      await apiClient.get<unknown>(API_ROUTE.teachers.profileBranchHistory(profileId)),
    );
  },

  /** Cần quyền READ_PAYROLL, không phải quyền đọc giáo viên. */
  async getPay(profileId: string) {
    return teacherPaySchema.parse(await apiClient.get<unknown>(API_ROUTE.teachers.profilePay(profileId)));
  },

  async updatePay(profileId: string, payload: TeacherPayTermsPayload) {
    return teacherPaySchema.parse(await apiClient.put<unknown>(API_ROUTE.teachers.profilePay(profileId), payload));
  },
} as const;
