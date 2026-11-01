import { SUBMISSION_STATUS, type SubmissionStatus } from "@/entities/assessment/model/assessment-schema";

/**
 * "Thiếu bài" theo cách Google Classroom đánh dấu Missing: bài có hạn nộp, hạn đã qua mà học viên vẫn
 * chưa nộp. Bài không đặt hạn thì không bao giờ thiếu. Ngày so sánh dạng "YYYY-MM-DD" nên so chuỗi là đủ.
 */
export function isMissingWork(dueDate: string | null, status: SubmissionStatus | undefined, today: string): boolean {
  if (dueDate === null || dueDate >= today) {
    return false;
  }
  return status === undefined || status === SUBMISSION_STATUS.notSubmitted;
}
