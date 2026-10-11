export const academicKeys = {
  all: ["academic"] as const,
  gradebooks: () => [...academicKeys.all, "gradebook"] as const,
  gradebook: (classId: string) => [...academicKeys.gradebooks(), classId] as const,
  progresses: () => [...academicKeys.all, "progress"] as const,
  progress: (classId: string) => [...academicKeys.progresses(), classId] as const,
  studentSummaries: () => [...academicKeys.all, "student-summary"] as const,
  studentSummary: (studentProfileId: string) => [...academicKeys.studentSummaries(), studentProfileId] as const,
} as const;
