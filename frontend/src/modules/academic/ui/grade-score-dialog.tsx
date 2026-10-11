import type { StudentGradesRow } from "@/entities/academic";
import { useRecordGrade } from "@/modules/academic/api/use-academic-mutations";
import { createRecordGradeFormSchema } from "@/modules/academic/model/academic-forms";
import type { GradeColumn } from "@/shared/api/schemas";
import { useZodForm } from "@/shared/lib/use-zod-form";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { useMemo } from "react";

export interface GradeScoreDialogProps {
  readonly classId: string;
  readonly row: StudentGradesRow;
  readonly column: GradeColumn;
  readonly open: boolean;
  readonly onClose: () => void;
}

export function GradeScoreDialog({ classId, row, column, open, onClose }: GradeScoreDialogProps) {
  const recordGrade = useRecordGrade(classId);
  const schema = useMemo(() => createRecordGradeFormSchema(column.maxScore), [column.maxScore]);
  const currentScore = row.scoresByColumnId[column.id];

  const form = useZodForm({
    schema,
    initialValues: { score: currentScore === undefined ? "" : String(currentScore) },
    onSubmit: async (values) => {
      await recordGrade.mutateAsync({
        classId,
        enrollmentId: row.enrollmentId,
        courseGradeColumnId: column.id,
        score: values.score,
      });
      onClose();
    },
  });

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title={`Điểm ${column.name}`}
      description={`${row.studentName ?? "Học viên"} · thang điểm ${column.maxScore} · trọng số ${column.weightPercent}%`}
    >
      <form id="record-grade-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Điểm" htmlFor="record-grade-score" error={form.fieldErrors.score}>
          <GlassInput
            id="record-grade-score"
            autoFocus
            type="number"
            min={0}
            max={column.maxScore}
            step={0.01}
            value={form.values.score}
            invalid={form.fieldErrors.score !== undefined}
            onChange={(event) => form.setValue("score", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="record-grade-form" loading={form.isSubmitting}>
          Lưu điểm
        </GlassButton>
      </footer>
    </GlassModal>
  );
}
