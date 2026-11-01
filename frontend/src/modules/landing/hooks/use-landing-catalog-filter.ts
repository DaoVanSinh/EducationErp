import type { LandingBranch, LandingClass, LandingCourse } from "@/modules/landing/model/landing-schema";
import { useMemo, useState, type ChangeEvent } from "react";

export const COURSE_FILTER_ALL = "all";
export const COURSE_FILTER_OPEN = "open-classes";

export interface CourseFilterCategory {
  readonly id: string;
  readonly label: string;
}

function matchesCourseFilter(
  course: LandingCourse,
  selectedCategory: string,
  courseIdsInBranch: ReadonlySet<string>,
): boolean {
  if (selectedCategory === COURSE_FILTER_ALL) return true;
  if (selectedCategory === COURSE_FILTER_OPEN) return course.openClassCount > 0;
  return courseIdsInBranch.has(course.id);
}

function matchesCourseSearch(course: LandingCourse, query: string): boolean {
  if (!query) return true;
  const description = course.description ?? "";
  return (
    course.name.toLowerCase().includes(query) ||
    course.code.toLowerCase().includes(query) ||
    description.toLowerCase().includes(query)
  );
}

export function useLandingCatalogFilter(
  courses: readonly LandingCourse[],
  classes: readonly LandingClass[],
  branches: readonly LandingBranch[],
) {
  const [selectedCategory, setSelectedCategory] = useState<string>(COURSE_FILTER_ALL);
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [selectedCourseId, setSelectedCourseId] = useState<string | null>(null);

  const categories: readonly CourseFilterCategory[] = useMemo(() => {
    const base: CourseFilterCategory[] = [
      { id: COURSE_FILTER_ALL, label: "Tất cả khoá học" },
      { id: COURSE_FILTER_OPEN, label: "Có lớp đang tuyển sinh" },
    ];
    const branchPills = branches.map((b) => ({
      id: b.id,
      label: b.name,
    }));
    return [...base, ...branchPills];
  }, [branches]);

  const courseIdsInSelectedBranch = useMemo(() => {
    const ids = new Set<string>();
    for (const cls of classes) {
      if (cls.branchId === selectedCategory) {
        ids.add(cls.courseId);
      }
    }
    return ids;
  }, [classes, selectedCategory]);

  const filteredCourses = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();
    return courses.filter(
      (course) =>
        matchesCourseFilter(course, selectedCategory, courseIdsInSelectedBranch) &&
        matchesCourseSearch(course, normalizedQuery),
    );
  }, [courses, selectedCategory, courseIdsInSelectedBranch, searchQuery]);

  const selectedCourse = useMemo(
    () => courses.find((c) => c.id === selectedCourseId) ?? null,
    [courses, selectedCourseId],
  );

  const selectedCourseClasses = useMemo(
    () => (selectedCourseId ? classes.filter((cls) => cls.courseId === selectedCourseId) : []),
    [classes, selectedCourseId],
  );

  const handleSearchChange = (event: ChangeEvent<HTMLInputElement>) => {
    setSearchQuery(event.target.value);
  };

  return {
    categories,
    selectedCategory,
    searchQuery,
    filteredCourses,
    selectedCourse,
    selectedCourseClasses,
    onSelectCategory: setSelectedCategory,
    onSearchChange: handleSearchChange,
    onOpenCourseDetail: (course: LandingCourse) => setSelectedCourseId(course.id),
    onCloseCourseDetail: () => setSelectedCourseId(null),
  };
}
