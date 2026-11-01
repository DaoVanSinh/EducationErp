import { useSession } from "@/entities/account";
import { useCourses } from "@/entities/course";
import { usePermissions } from "@/entities/permission";
import { useRbacCatalog } from "@/entities/rbac-catalog";
import { useCreateClass } from "@/modules/courses/api/use-courses-mutations";
import { useTeacherOptions } from "@/modules/courses/hooks/use-teacher-options";
import { planEndDate } from "@/modules/courses/model/class-end-date-plan";
import { createClassFormSchema } from "@/modules/courses/model/courses-forms";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { todayLocalIsoDate } from "@/shared/lib/local-date";
import { useZodForm } from "@/shared/lib/use-zod-form";

/** Đủ lớn để lấy hết danh mục khoá học của một trung tâm trong một lần. */
const COURSE_OPTION_PAGE_SIZE = 100;
const DEFAULT_MIN_SEATS = "1";
const AUTO_END_DATE_FIELDS: ReadonlySet<string> = new Set(["courseId", "startDate", "schedule"]);

/**
 * Mở lớp: lớp bắt đầu ở trạng thái Đang tuyển sinh. Người có quyền cấp tổ chức chọn chi nhánh từ danh mục;
 * người chỉ quản lý một chi nhánh thì lớp luôn mở ở chi nhánh của họ (backend chặn chi nhánh khác).
 */
export function useCreateClassController({ onClose }: { readonly onClose: () => void }) {
  const createClass = useCreateClass();
  const courses = useCourses(0, COURSE_OPTION_PAGE_SIZE);
  const teacherOptions = useTeacherOptions();
  const permissions = usePermissions();
  const canChooseBranch = permissions.allows(ACCESS_RULE.readRole);
  const catalog = useRbacCatalog({ enabled: canChooseBranch });
  const session = useSession();
  const ownBranchId = session.data?.branchId ?? "";

  const form = useZodForm({
    schema: createClassFormSchema,
    initialValues: {
      courseId: "",
      code: "",
      branchId: canChooseBranch ? "" : ownBranchId,
      teacherId: "",
      maxSeats: "",
      minSeats: DEFAULT_MIN_SEATS,
      startDate: todayLocalIsoDate(),
      endDate: "",
      schedule: [],
    },
    onSubmit: async (values) => {
      await createClass.mutateAsync(values);
      form.reset();
      onClose();
    },
  });

  const courseList = courses.data?.items ?? [];
  const sessionCountOf = (courseId: string) =>
    courseList.find((course) => course.id === courseId)?.standardSessionCount ?? null;

  // Đổi khoá học / ngày khai giảng / lịch thì tính lại ngày kết thúc ngay trong lúc nhập, không cần effect.
  const setValue: typeof form.setValue = (field, value) => {
    form.setValue(field, value);
    if (!AUTO_END_DATE_FIELDS.has(String(field))) {
      return;
    }
    const next = { ...form.values, [field]: value };
    const plan = planEndDate(next.startDate, next.schedule, sessionCountOf(next.courseId));
    if (plan.endDate !== null) {
      form.setValue("endDate", plan.endDate);
    }
  };

  return {
    values: form.values,
    fieldErrors: form.fieldErrors,
    submitError: form.submitError,
    isSubmitting: form.isSubmitting,
    handleSubmit: form.handleSubmit,
    setValue,
    endDateHint: planEndDate(form.values.startDate, form.values.schedule, sessionCountOf(form.values.courseId)).hint,
    courses: courseList,
    teacherOptions,
    canChooseBranch,
    branches: catalog.data?.branches ?? [],
    fixedBranchLabel: session.data?.branchName ?? "Chi nhánh của bạn",
    minStartDate: todayLocalIsoDate(),
  };
}
