import { useCreateEnrollment } from "@/modules/enrollment/api/use-enrollment-mutations";
import { createEnrollmentFormSchema } from "@/modules/enrollment/model/enrollment-forms";
import { ApiError } from "@/shared/api/api-error";
import { ERROR_CODE } from "@/shared/constants/error-codes";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { useCallback, useState } from "react";

const INITIAL_VALUES = { studentProfileId: "", classId: "" };

interface EnrollmentSelection {
  readonly studentProfileId: string;
  readonly classId: string;
}

/** Cảnh báo trùng lịch gắn với đúng một cặp học viên và lớp. */
interface ScheduleConflict extends EnrollmentSelection {
  readonly message: string;
}

function isSameSelection(conflict: ScheduleConflict | null, selection: EnrollmentSelection): boolean {
  return conflict?.studentProfileId === selection.studentProfileId && conflict.classId === selection.classId;
}

function isScheduleConflictError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.errorCode === ERROR_CODE.enrollmentScheduleConflict;
}

/**
 * Luồng ghi danh hai bước khi trùng lịch: lần gửi đầu nhận cảnh báo, lần gửi sau cho đúng lựa chọn đó mới là xác
 * nhận chấp nhận trùng. Đổi học viên hoặc lớp thì cảnh báo mất hiệu lực, nên không thể lỡ tay xác nhận cho một
 * lựa chọn khác với lựa chọn đã được cảnh báo.
 */
export function useCreateEnrollmentForm(onClose: () => void) {
  const createEnrollment = useCreateEnrollment();
  const [conflict, setConflict] = useState<ScheduleConflict | null>(null);

  const form = useZodForm({
    schema: createEnrollmentFormSchema,
    initialValues: INITIAL_VALUES,
    onSubmit: async (values) => {
      try {
        await createEnrollment.mutateAsync({
          ...values,
          acceptScheduleConflict: isSameSelection(conflict, values),
        });
      } catch (error) {
        if (!isScheduleConflictError(error)) {
          throw error;
        }
        setConflict({ ...values, message: error.message });
        return;
      }
      setConflict(null);
      form.reset();
      onClose();
    },
  });

  const cancel = useCallback(() => {
    setConflict(null);
    onClose();
  }, [onClose]);

  return {
    form,
    cancel,
    conflictMessage: isSameSelection(conflict, form.values) ? conflict?.message : undefined,
  };
}
