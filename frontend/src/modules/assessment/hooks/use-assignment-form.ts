import { ASSIGNMENT_TYPE } from "@/entities/assessment";
import { useCreateAssignment, useUpdateAssignment } from "@/modules/assessment/api/use-assessment-mutations";
import type { AssignmentFormTarget } from "@/modules/assessment/hooks/use-class-assignments-controller";
import { assignmentFormSchema, type AssignmentFormInput } from "@/modules/assessment/model/assessment-forms";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useMemo } from "react";

const DEFAULT_MAX_SCORE = "10";

function initialValues(target: AssignmentFormTarget): AssignmentFormInput {
  if (target === "new") {
    return { title: "", description: "", type: ASSIGNMENT_TYPE.homework, dueDate: "", maxScore: DEFAULT_MAX_SCORE, active: true };
  }
  return {
    title: target.title,
    description: target.description ?? "",
    type: target.type,
    dueDate: target.dueDate ?? "",
    maxScore: String(target.maxScore),
    active: target.active,
  };
}

/** Form giao bài mới hoặc sửa một bài (đổi thông tin, tắt/bật). */
export function useAssignmentForm(classId: string, target: AssignmentFormTarget, onDone: () => void) {
  const createAssignment = useCreateAssignment(classId);
  const updateAssignment = useUpdateAssignment(classId);
  const initial = useMemo(() => initialValues(target), [target]);

  const form = useZodForm({
    schema: assignmentFormSchema,
    initialValues: initial,
    onSubmit: async (values) => {
      const payload = {
        title: values.title,
        description: values.description,
        type: values.type,
        dueDate: values.dueDate,
        maxScore: values.maxScore,
      };
      if (target === "new") {
        await createAssignment.mutateAsync({ ...payload, classId });
      } else {
        await updateAssignment.mutateAsync({ assignmentId: target.id, payload: { ...payload, active: values.active } });
      }
      onDone();
    },
  });

  return { form, isNew: target === "new" };
}
