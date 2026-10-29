export const assessmentKeys = {
  all: ["assessment"] as const,
  classAssignments: (classId: string) => [...assessmentKeys.all, "class-assignments", classId] as const,
  classMiniTasks: (classId: string) => [...assessmentKeys.all, "class-mini-tasks", classId] as const,
  mine: () => [...assessmentKeys.all, "mine"] as const,
  practice: (miniTaskId: string) => [...assessmentKeys.all, "practice", miniTaskId] as const,
} as const;
