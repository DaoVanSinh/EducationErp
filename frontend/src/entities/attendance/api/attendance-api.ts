import {
  classAttendanceSchema,
  myAttendanceSchema,
  taughtClassSchema,
  type RecordAttendancePayload,
} from "@/entities/attendance/model/attendance-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const taughtClassListSchema = z.array(taughtClassSchema);

export const attendanceApi = {
  async getClassAttendance(classId: string) {
    return classAttendanceSchema.parse(await apiClient.get<unknown>(API_ROUTE.attendance.classAttendance(classId)));
  },

  async recordSession(payload: RecordAttendancePayload): Promise<void> {
    await apiClient.post<void>(API_ROUTE.attendance.sessions, payload);
  },

  /** Cổng học tập - backend tự lọc theo học viên đang đăng nhập. */
  async listMyTaughtClasses() {
    return taughtClassListSchema.parse(await apiClient.get<unknown>(API_ROUTE.attendance.myTaughtClasses));
  },

  async listMyAttendance() {
    return myAttendanceSchema.parse(await apiClient.get<unknown>(API_ROUTE.attendance.myClasses));
  },
} as const;
