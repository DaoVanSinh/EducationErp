import { ApiError } from "@/shared/api/api-error";
import { type FormEvent, useCallback, useState } from "react";
import type { z } from "zod";

export interface ZodFormOptions<TSchema extends z.ZodTypeAny> {
  readonly schema: TSchema;
  readonly initialValues: z.input<TSchema>;
  readonly onSubmit: (values: z.output<TSchema>) => Promise<void> | void;
}

export interface ZodForm<TSchema extends z.ZodTypeAny> {
  readonly values: z.input<TSchema>;
  readonly fieldErrors: Readonly<Record<string, string>>;
  readonly submitError: unknown;
  readonly isSubmitting: boolean;
  setValue: <TField extends keyof z.input<TSchema>>(field: TField, value: z.input<TSchema>[TField]) => void;
  handleSubmit: (event: FormEvent<HTMLFormElement>) => void;
  reset: () => void;
}

/**
 * Cầu nối giữa form và Zod: cùng một schema vừa kiểm tra phía client vừa là kiểu của payload gửi đi.
 *
 * Lỗi từng ô có hai nguồn và cùng đổ về một chỗ: Zod khi bấm gửi, và VALIDATION_FAILED của backend
 * khi server thấy điều client không thấy (email đã tồn tại, mật khẩu cũ sai).
 */
export function useZodForm<TSchema extends z.ZodTypeAny>({
  schema,
  initialValues,
  onSubmit,
}: ZodFormOptions<TSchema>): ZodForm<TSchema> {
  const [values, setValues] = useState<z.input<TSchema>>(initialValues);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const setValue = useCallback(
    <TField extends keyof z.input<TSchema>>(field: TField, value: z.input<TSchema>[TField]) => {
      setValues((previous) => ({ ...previous, [field]: value }) as z.input<TSchema>);
      // Xoá lỗi của chính ô đang sửa: giữ lại chỉ làm người dùng tưởng chưa sửa được gì.
      setFieldErrors((previous) => {
        const key = String(field);
        if (!(key in previous)) {
          return previous;
        }
        const next = { ...previous };
        delete next[key];
        return next;
      });
    },
    [],
  );

  const reset = useCallback(() => {
    setValues(initialValues);
    setFieldErrors({});
    setSubmitError(null);
  }, [initialValues]);

  const handleSubmit = useCallback(
    (event: FormEvent<HTMLFormElement>) => {
      event.preventDefault();
      const parsed = schema.safeParse(values);
      if (!parsed.success) {
        const issues = parsed.error.issues.reduce<Record<string, string>>((carry, issue) => {
          const field = issue.path.map(String).join(".");
          if (field.length > 0 && !(field in carry)) {
            carry[field] = issue.message;
          }
          return carry;
        }, {});
        setFieldErrors(issues);
        setSubmitError(null);
        return;
      }

      setIsSubmitting(true);
      setSubmitError(null);
      void Promise.resolve(onSubmit(parsed.data as z.output<TSchema>))
        .catch((error: unknown) => {
          setSubmitError(error);
          if (error instanceof ApiError) {
            setFieldErrors(error.fieldIssueMap());
          }
        })
        .finally(() => {
          setIsSubmitting(false);
        });
    },
    [onSubmit, schema, values],
  );

  return { values, fieldErrors, submitError, isSubmitting, setValue, handleSubmit, reset };
}
