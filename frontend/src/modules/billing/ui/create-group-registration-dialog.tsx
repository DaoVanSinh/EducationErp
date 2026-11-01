import { MIN_GROUP_MEMBERS } from "@/entities/billing";
import { useCreateGroupRegistrationController } from "@/modules/billing/hooks/use-create-group-registration-controller";
import { formatter } from "@/shared/lib/format";
import { CheckableRow } from "@/shared/ui/checkable-row";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { FormField } from "@/shared/ui/form-field";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassModal } from "@/shared/ui/glass-modal";
import { GlassSelect } from "@/shared/ui/glass-select";
import { Skeleton } from "@/shared/ui/skeleton";

type Controller = ReturnType<typeof useCreateGroupRegistrationController>;

export interface CreateGroupRegistrationDialogProps {
  readonly open: boolean;
  readonly onClose: () => void;
}

export function CreateGroupRegistrationDialog({ open, onClose }: CreateGroupRegistrationDialogProps) {
  const controller = useCreateGroupRegistrationController(onClose);
  const { form } = controller;

  return (
    <GlassModal
      open={open}
      onClose={onClose}
      title="Lập nhóm học chung"
      description={`Từ ${MIN_GROUP_MEMBERS} học viên cùng học một lớp. Mỗi người tự đóng học phí của mình, được giảm theo số người trong nhóm.`}
    >
      <form id="create-group-form" onSubmit={form.handleSubmit} className="flex flex-col gap-4" noValidate>
        {form.submitError ? <ErrorNotice error={form.submitError} /> : null}

        <FormField label="Lớp học chung" htmlFor="group-class" error={form.fieldErrors.classId}>
          <GlassSelect
            id="group-class"
            className="w-full"
            containerClassName="w-full"
            value={form.values.classId}
            invalid={form.fieldErrors.classId !== undefined}
            onChange={(event) => controller.onSelectClass(event.target.value)}
          >
            <option value="">Chọn lớp</option>
            {controller.classOptions.map((cls) => (
              <option key={cls.id} value={cls.id}>
                {cls.code} · {cls.courseName}
              </option>
            ))}
          </GlassSelect>
        </FormField>

        <FormField
          label="Học viên trong nhóm"
          htmlFor="group-members"
          hint="Chỉ hiện học viên đang học lớp đã chọn và chưa phát hành hoá đơn."
          error={form.fieldErrors.enrollmentIds}
        >
          <CandidateList controller={controller} />
        </FormField>

        <PricePreview controller={controller} />
      </form>

      <footer className="flex justify-end gap-2">
        <GlassButton variant="ghost" onClick={onClose}>
          Huỷ
        </GlassButton>
        <GlassButton type="submit" form="create-group-form" loading={form.isSubmitting}>
          Lập nhóm
        </GlassButton>
      </footer>
    </GlassModal>
  );
}

function CandidateList({ controller }: { readonly controller: Controller }) {
  if (controller.form.values.classId.length === 0) {
    return <p className="text-xs text-mist-500">Chọn lớp trước để xem học viên đang học.</p>;
  }
  if (controller.isLoadingCandidates) {
    return <Skeleton className="h-20" />;
  }
  if (controller.candidates.length < MIN_GROUP_MEMBERS) {
    return (
      <p className="text-xs text-mist-500">
        Lớp này chưa có đủ {MIN_GROUP_MEMBERS} học viên đang học. Ghi danh các bạn vào lớp trước, rồi quay lại lập nhóm.
      </p>
    );
  }
  return (
    <div id="group-members" className="flex max-h-56 flex-col gap-2 overflow-y-auto">
      {controller.candidates.map((candidate) => (
        <CheckableRow
          key={candidate.enrollmentId}
          checked={controller.selectedIds.includes(candidate.enrollmentId)}
          onToggle={() => controller.onToggleEnrollment(candidate.enrollmentId)}
          label={candidate.label}
        />
      ))}
    </div>
  );
}

/** Xem trước; con số chốt do backend tính lúc tạo (CreateGroupRegistration snapshot). */
function PricePreview({ controller }: { readonly controller: Controller }) {
  if (controller.selectedIds.length < MIN_GROUP_MEMBERS) {
    return null;
  }
  if (controller.discountPercent === null) {
    return (
      <p className="text-xs text-amber-600">
        Chưa cấu hình bậc giảm giá cho {controller.selectedIds.length} suất học — lập nhóm sẽ bị từ chối. Thêm bậc ở
        trang Bậc giảm giá trước.
      </p>
    );
  }
  return (
    <dl className="glass grid grid-cols-1 gap-2 rounded-2xl p-3 sm:grid-cols-3">
      <div>
        <dt className="text-xs text-mist-500">Học phí gốc / người</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.tuitionFee ?? 0)} đ</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Giảm nhóm {controller.selectedIds.length} người</dt>
        <dd className="text-sm text-mist-100">{controller.discountPercent}%</dd>
      </div>
      <div>
        <dt className="text-xs text-mist-500">Mỗi người đóng</dt>
        <dd className="text-sm text-mist-100">{formatter.count(controller.perPersonFee ?? 0)} đ</dd>
      </div>
    </dl>
  );
}
