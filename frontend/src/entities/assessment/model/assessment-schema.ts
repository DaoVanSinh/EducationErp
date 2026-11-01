import { ENROLLMENT_STATUS } from "@/shared/api/schemas";
import { z } from "zod";

/** Bản sao của AssessmentConstants.AssignmentType. */
export const ASSIGNMENT_TYPE = {
  homework: "HOMEWORK",
  miniTest: "MINI_TEST",
} as const;

export type AssignmentType = (typeof ASSIGNMENT_TYPE)[keyof typeof ASSIGNMENT_TYPE];

export const ASSIGNMENT_TYPE_LABEL: Record<AssignmentType, string> = {
  [ASSIGNMENT_TYPE.homework]: "Bài tập",
  [ASSIGNMENT_TYPE.miniTest]: "Mini-test",
};

/** Bản sao của AssessmentConstants.SubmissionStatus. */
export const SUBMISSION_STATUS = {
  submitted: "SUBMITTED",
  late: "LATE",
  notSubmitted: "NOT_SUBMITTED",
} as const;

export type SubmissionStatus = (typeof SUBMISSION_STATUS)[keyof typeof SUBMISSION_STATUS];

export const SUBMISSION_STATUS_LABEL: Record<SubmissionStatus, string> = {
  [SUBMISSION_STATUS.submitted]: "Đã nộp",
  [SUBMISSION_STATUS.late]: "Nộp muộn",
  [SUBMISSION_STATUS.notSubmitted]: "Chưa nộp",
};

export const SUBMISSION_STATUS_ORDER: readonly SubmissionStatus[] = [
  SUBMISSION_STATUS.submitted,
  SUBMISSION_STATUS.late,
  SUBMISSION_STATUS.notSubmitted,
];

/** Khớp AssessmentConstants.Limits ở backend. */
export const ASSESSMENT_LIMITS = {
  minOptions: 2,
  maxOptions: 6,
  maxQuestions: 50,
  minPassPercent: 1,
  maxPassPercent: 100,
  defaultPassPercent: 80,
} as const;

const assignmentTypeSchema = z.enum([ASSIGNMENT_TYPE.homework, ASSIGNMENT_TYPE.miniTest]);
const submissionStatusSchema = z.enum([
  SUBMISSION_STATUS.submitted,
  SUBMISSION_STATUS.late,
  SUBMISSION_STATUS.notSubmitted,
]);
const enrollmentStatusSchema = z.enum([
  ENROLLMENT_STATUS.active,
  ENROLLMENT_STATUS.withdrawn,
  ENROLLMENT_STATUS.completed,
]);

export const assignmentSchema = z.object({
  id: z.string().uuid(),
  classId: z.string().uuid(),
  title: z.string(),
  description: z.string().nullable(),
  type: assignmentTypeSchema,
  dueDate: z.string().nullable(),
  maxScore: z.number(),
  active: z.boolean(),
  createdAt: z.string(),
  submittedCount: z.number().int(),
  rosterCount: z.number().int(),
  averageScore: z.number().nullable(),
});

export type Assignment = z.infer<typeof assignmentSchema>;

export const resultCellSchema = z.object({
  status: submissionStatusSchema,
  score: z.number().nullable(),
  feedback: z.string().nullable(),
});

export type ResultCell = z.infer<typeof resultCellSchema>;

/** Khuyết khoá assignmentId nghĩa là CHƯA nộp. `completionPercent` null = lớp chưa có bài nào. */
export const studentAssignmentRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  enrollmentStatus: enrollmentStatusSchema,
  resultsByAssignmentId: z.record(z.string(), resultCellSchema),
  submittedCount: z.number().int(),
  completionPercent: z.number().int().nullable(),
});

export type StudentAssignmentRow = z.infer<typeof studentAssignmentRowSchema>;

export const classAssignmentsSchema = z.object({
  classId: z.string().uuid(),
  assignments: z.array(assignmentSchema),
  rows: z.array(studentAssignmentRowSchema),
});

export type ClassAssignments = z.infer<typeof classAssignmentsSchema>;

/** `correctOptionIndex` và `explanation` null khi trả cho học viên đang làm bài. */
export const questionSchema = z.object({
  id: z.string().uuid(),
  content: z.string(),
  options: z.array(z.string()),
  correctOptionIndex: z.number().int().nullable(),
  explanation: z.string().nullable(),
});

export type Question = z.infer<typeof questionSchema>;

export const miniTaskSchema = z.object({
  id: z.string().uuid(),
  classId: z.string().uuid(),
  title: z.string(),
  description: z.string().nullable(),
  active: z.boolean(),
  passPercent: z.number().int(),
  createdAt: z.string(),
  questions: z.array(questionSchema),
  attemptCount: z.number().int(),
  studentsAttempted: z.number().int(),
  studentsPassed: z.number().int(),
  averageBestScorePercent: z.number().int().nullable(),
});

export type MiniTask = z.infer<typeof miniTaskSchema>;

export const studentMiniTaskRowSchema = z.object({
  enrollmentId: z.string().uuid(),
  studentProfileId: z.string().uuid(),
  studentName: z.string().nullable(),
  enrollmentStatus: enrollmentStatusSchema,
  bestScoreByMiniTaskId: z.record(z.string(), z.number().int()),
  attemptsByMiniTaskId: z.record(z.string(), z.number().int()),
});

export type StudentMiniTaskRow = z.infer<typeof studentMiniTaskRowSchema>;

export const classMiniTasksSchema = z.object({
  classId: z.string().uuid(),
  miniTasks: z.array(miniTaskSchema),
  rows: z.array(studentMiniTaskRowSchema),
});

export type ClassMiniTasks = z.infer<typeof classMiniTasksSchema>;

export const myAssignmentItemSchema = z.object({
  assignmentId: z.string().uuid(),
  title: z.string(),
  description: z.string().nullable(),
  type: assignmentTypeSchema,
  dueDate: z.string().nullable(),
  maxScore: z.number(),
  status: submissionStatusSchema,
  score: z.number().nullable(),
  feedback: z.string().nullable(),
});

export type MyAssignmentItem = z.infer<typeof myAssignmentItemSchema>;

export const myMiniTaskItemSchema = z.object({
  miniTaskId: z.string().uuid(),
  title: z.string(),
  description: z.string().nullable(),
  questionCount: z.number().int(),
  attemptCount: z.number().int(),
  bestScorePercent: z.number().int().nullable(),
  lastScorePercent: z.number().int().nullable(),
  passPercent: z.number().int(),
  passed: z.boolean(),
});

export type MyMiniTaskItem = z.infer<typeof myMiniTaskItemSchema>;

export const myAssessmentClassSchema = z.object({
  enrollmentId: z.string().uuid(),
  classId: z.string().uuid(),
  classCode: z.string().nullable(),
  courseName: z.string().nullable(),
  enrollmentStatus: enrollmentStatusSchema,
  assignments: z.array(myAssignmentItemSchema),
  miniTasks: z.array(myMiniTaskItemSchema),
  submittedCount: z.number().int(),
  completionPercent: z.number().int().nullable(),
});

export type MyAssessmentClass = z.infer<typeof myAssessmentClassSchema>;

export const myAssessmentsSchema = z.array(myAssessmentClassSchema);

export const practiceMiniTaskSchema = z.object({
  id: z.string().uuid(),
  classId: z.string().uuid(),
  title: z.string(),
  description: z.string().nullable(),
  passPercent: z.number().int(),
  questions: z.array(questionSchema),
});

export type PracticeMiniTask = z.infer<typeof practiceMiniTaskSchema>;

export const attemptResultSchema = z.object({
  attemptId: z.string().uuid(),
  correctCount: z.number().int(),
  totalQuestions: z.number().int(),
  scorePercent: z.number().int(),
  passPercent: z.number().int(),
  passed: z.boolean(),
  submittedAt: z.string(),
  results: z.array(
    z.object({
      questionId: z.string().uuid(),
      selectedOptionIndex: z.number().int().nullable(),
      correctOptionIndex: z.number().int(),
      correct: z.boolean(),
      explanation: z.string().nullable(),
    }),
  ),
});

export type AttemptResult = z.infer<typeof attemptResultSchema>;

export const createdResponseSchema = z.object({ id: z.string().uuid() });

export interface AssignmentPayload {
  readonly title: string;
  readonly description: string | null;
  readonly type: AssignmentType;
  readonly dueDate: string | null;
  readonly maxScore: number;
}

export interface CreateAssignmentPayload extends AssignmentPayload {
  readonly classId: string;
}

export interface UpdateAssignmentPayload extends AssignmentPayload {
  readonly active: boolean;
}

export interface ResultEntryPayload {
  readonly enrollmentId: string;
  readonly status: SubmissionStatus;
  readonly score: number | null;
  readonly feedback: string | null;
}

export interface QuestionPayload {
  readonly content: string;
  readonly options: readonly string[];
  readonly correctOptionIndex: number;
  readonly explanation: string | null;
}

export interface MiniTaskPayload {
  readonly title: string;
  readonly description: string | null;
  readonly passPercent: number;
  readonly questions: readonly QuestionPayload[];
}

export interface CreateMiniTaskPayload extends MiniTaskPayload {
  readonly classId: string;
}

export interface UpdateMiniTaskPayload extends MiniTaskPayload {
  readonly active: boolean;
}
