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

/** Bộ lọc tuỳ chọn của danh sách lớp. Chi nhánh không có ở đây: backend tự lọc theo phạm vi quyền. */
export interface ClassListFilter {
  readonly teacherId?: string;
  /** Chỉ lớp còn nhận ghi danh (đang tuyển sinh/đang học) - cho ô chọn lớp khi ghi danh. */
  readonly openOnly?: boolean;
}

export const classApi = {
  async listClasses(page: number, size: number, filter: ClassListFilter = {}) {
    return classPageSchema.parse(
      await apiClient.get<unknown>(API_ROUTE.courses.classes, {
        page,
        size,
        teacherId: filter.teacherId,
        openOnly: filter.openOnly,
      }),
    );
  },

  /** Cổng giảng dạy: backend tự lọc theo giáo viên đang đăng nhập, không nhận teacherId từ client. */
  async listMyTeachingClasses(page: number, size: number) {
    return classPageSchema.parse(await apiClient.get<unknown>(API_ROUTE.courses.myClasses, { page, size }));
  },

  async createClass(payload: CreateClassPayload): Promise<string> {
    return createdClassIdSchema.parse(
      await apiClient.post<unknown>(API_ROUTE.courses.classes, payload),
    );
  },

  async updateClass(classId: string, payload: UpdateClassPayload): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.courses.class(classId), payload);
  },

  /** Khai giảng: đang tuyển sinh, đã tới ngày khai giảng, có lịch học và đủ sĩ số tối thiểu. */
  async startClass(classId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.courses.classStart(classId));
  },

  /** Kết thúc lớp đang học - ghi danh đang học chuyển sang hoàn tất. */
  async completeClass(classId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.courses.classComplete(classId));
  },

  /** Huỷ lớp chưa khai giảng - ghi danh đang học chuyển sang đã rút. */
  async cancelClass(classId: string): Promise<void> {
    await apiClient.post<void>(API_ROUTE.courses.classCancel(classId));
  },
} as const;
