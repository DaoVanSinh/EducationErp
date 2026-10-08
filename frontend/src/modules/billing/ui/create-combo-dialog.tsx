import { MIN_COMBO_ENROLLMENTS } from "@/entities/billing";
import { useCreateComboController } from "@/modules/billing/hooks/use-create-combo-controller";
import { formatter } from "@/shared/lib/format";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassInput } from "@/shared/ui/glass-input";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";
import { Skeleton } from "@/shared/ui/skeleton";

export interface CreateComboDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateComboDialog({ open, onClose }: CreateComboDialogProps) {
  const controller = useCreateComboController(onClose);
  const { form } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Tạo combo khoá học"
      description={`Chọn từ ${MIN_COMBO_ENROLLMENTS} khoá đang học của cùng một học viên. Combo thu tối đa 3 đợt, một hạn đóng chung.`}
    >
      <form id="create-combo-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Học viên" htmlFor="combo-student" error={form.fieldErrors.studentProfileId}>
          <GlassSelect
            id="combo-student"
            className="w-full"
            containerClassName="w-full"
            value={form.values.studentProfileId}
            invalid={form.fieldErrors.studentProfileId !== undefined}
            onChange={(event) => controller.onSelectStudent(event.target.value)}
          >
            <option value="">Chọn học viên</option>
            {(controller.students.data?.items ?? [])
              .filter((student) => student.active)
              .map((student) => (
                <option key={student.id} value={student.id}>
                  {student.fullName ?? student.email ?? student.id}
                </option>
              ))}
          </GlassSelect>
        </FormField>

        <FormField
          label="Khoá đang học"
          htmlFor="combo-enrollments"
          hint="Chỉ hiện ghi danh đang học của học viên đã chọn."
          error={form.fieldErrors.enrollmentIds}
        >
          <ComboCandidateList controller={controller} />
        </FormField>

        <ComboPricePreview controller={controller} />

        <FormField label="Hạn đóng của combo" htmlFor="combo-due-date" error={form.fieldErrors.dueDate}>
          <GlassInput
            id="combo-due-date"
            type="date"
            value={form.values.dueDate}
            invalid={form.fieldErrors.dueDate !== undefined}
            onChange={(event) => form.setValue("dueDate", event.target.value)}
          />
        </FormField>
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-combo-form" loading={form.isSubmitting}>
          Tạo combo
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

/** Tách ra để dialog không rẽ nhánh trong JSX (Mandate #3) và giữ mỗi component dưới 200 dòng. */
function ComboCandidateList({
  controller,
}: {
  readonly controller: ReturnType<typeof useCreateComboController>;
}) {
  if (controller.form.values.studentProfileId.length === 0) {
    return <p className="text-xs text-mist-500">Chọn học viên trước để xem các khoá đang học.</p>;
  }
  if (controller.isLoadingCandidates) {
    return <Skeleton className="h-20" />;
  }
  if (controller.candidates.length === 0) {
    return (
      <p className="text-xs text-mist-500">
        Học viên này chưa có ghi danh đang học nào. Ghi danh các khoá trước, rồi quay lại gộp combo.
      </p>
    );
  }
  return (
    <div id="combo-enrollments" className="flex max-h-56 flex-col gap-2 overflow-y-auto">
      {controller.candidates.map((candidate) => (
        <CheckableRow
          key={candidate.enrollmentId}
          checked={controller.selectedIds.includes(candidate.enrollmentId)}
          onToggle={() => controller.onToggleEnrollment(candidate.enrollmentId)}
          label={candidate.label}
          description={
            candidate.tuitionFee === null
              ? "Khoá chưa gắn học phí — không gộp được"
              : `${formatter.count(candidate.tuitionFee)} đ`
          }
        />
      ))}
    </div>
  );
}

/** Xem trước; con số chốt do backend tính lúc tạo (CreateCombo snapshot vào Combo). */
function ComboPricePreview({
  controller,
}: {
  readonly controller: ReturnType<typeof useCreateComboController>;
}) {
  if (controller.selectedIds.length === 0) {
    return null;
  }
  if (controller.discountPercent === null) {
    return (
      <p className="text-xs text-amber-600">
        Chưa cấu hình bậc giảm giá cho {controller.selectedIds.length} khoá — tạo combo sẽ bị từ chối.
        Thêm bậc ở trang Bậc giảm giá combo trước.
      </p>
    );
  }
  return (
    <dl className="glass grid grid-cols-1 gap-2 rounded-2xl p-3 sm:grid-cols-3">
      <div>
        <dt className="text-xs text-mist-500">Tổng gốc</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.totalOriginalAmount)} đ</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Giảm</dt>
        <dd className="text-sm text-mist-100">{controller.discountPercent}%</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Phải thu</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.discountedTotal ?? 0)} đ</dd>
      </div>
    </dl>
  );
}
