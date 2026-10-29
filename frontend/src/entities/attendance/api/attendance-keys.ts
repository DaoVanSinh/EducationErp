export const attendanceKeys = {
  all: ["attendance"] as const,
  classes: () => [...attendanceKeys.all, "class"] as const,
  class: (classId: string) => [...attendanceKeys.classes(), classId] as const,
  mine: () => [...attendanceKeys.all, "mine"] as const,
} as const;
