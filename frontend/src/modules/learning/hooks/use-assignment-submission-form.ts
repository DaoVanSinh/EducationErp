import { ASSESSMENT_LIMITS, type MyAssignmentItem } from "@/entities/assessment";
import { useSubmitAssignment } from "@/modules/learning/api/use-practice-mutation";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { z } from "zod";

const submissionFormSchema = z.object({
  content: z
    .string()
    .trim()
    .min(1, "Nhập câu trả lời hoặc đường dẫn tới bài làm")
    .max(ASSESSMENT_LIMITS.maxSubmissionLength, `Tối đa ${ASSESSMENT_LIMITS.maxSubmissionLength} ký tự`),
});

/** State của hộp thoại nộp bài: nộp lại thì điền sẵn nội dung đã nộp để học viên sửa tiếp (Mandate #2). */
export function useAssignmentSubmissionForm(assignment: MyAssignmentItem, onDone: () => void) {
  const submitAssignment = useSubmitAssignment();
  return useZodForm({
    schema: submissionFormSchema,
    initialValues: { content: assignment.submissionContent ?? "" },
    onSubmit: async (values) => {
      await submitAssignment.mutateAsync({ assignmentId: assignment.assignmentId, content: values.content });
      onDone();
    },
  });
}
