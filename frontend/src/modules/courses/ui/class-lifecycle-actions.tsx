import { CLASS_STATUS, hasEnoughStudents, isOpenClass, type ClassSummary } from "@/entities/class";
import { Can } from "@/entities/permission";
import { CLASS_TRANSITION, type ClassTransition } from "@/modules/courses/api/use-courses-mutations";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { GlassButton } from "@/shared/ui/glass-button";
import { CircleCheck, CircleX, Pencil, Play } from "lucide-react";

export interface ClassLifecycleActionsProps {
  readonly cls: ClassSummary;
  readonly today: string;
  readonly onEdit: (cls: ClassSummary) => void;
  readonly onTransition: (cls: ClassSummary, transition: ClassTransition) => void;
}

/**
 * Nút thao tác theo trạng thái lớp. Khai giảng chỉ bật khi đủ điều kiện (tới ngày, có lịch, đủ sĩ số tối
 * thiểu) - nút tắt kèm lý do ở `title` để admin biết còn thiếu gì; backend vẫn kiểm lại.
 */
export function ClassLifecycleActions({ cls, today, onEdit, onTransition }: ClassLifecycleActionsProps) {
  if (!isOpenClass(cls.status)) {
    return null;
  }
  return (
    <Can {...ACCESS_RULE.updateClass}>
      <div className="flex flex-wrap gap-1 justify-self-start lg:justify-self-end">
        <GlassButton variant="secondary" size="sm" onClick={() => onEdit(cls)} icon={<Pencil size={14} aria-hidden />}>
          Sửa
        </GlassButton>
        {cls.status === CLASS_STATUS.enrolling ? (
          <EnrollingActions cls={cls} today={today} onTransition={onTransition} />
        ) : (
          <GlassButton
            variant="secondary"
            size="sm"
            onClick={() => onTransition(cls, CLASS_TRANSITION.complete)}
            icon={<CircleCheck size={14} aria-hidden />}
          >
            Kết thúc
          </GlassButton>
        )}
      </div>
    </Can>
  );
}

function startBlocker(cls: ClassSummary, today: string): string | null {
  if (cls.startDate > today) {
    return "Chưa tới ngày khai giảng - sửa ngày khai giảng nếu muốn học sớm";
  }
  if (cls.schedule.length === 0) {
    return "Lớp chưa có lịch học hàng tuần";
  }
  if (!hasEnoughStudents(cls)) {
    return `Mới có ${cls.activeEnrollments}/${cls.minSeats} học viên tối thiểu`;
  }
  return null;
}

function EnrollingActions({
  cls,
  today,
  onTransition,
}: Pick<ClassLifecycleActionsProps, "cls" | "today" | "onTransition">) {
  const blocker = startBlocker(cls, today);
  return (
    <>
      <span title={blocker ?? "Chuyển lớp sang Đang học"}>
        <GlassButton
          size="sm"
          disabled={blocker !== null}
          onClick={() => onTransition(cls, CLASS_TRANSITION.start)}
          icon={<Play size={14} aria-hidden />}
        >
          Khai giảng
        </GlassButton>
      </span>
      <GlassButton
        variant="danger"
        size="sm"
        onClick={() => onTransition(cls, CLASS_TRANSITION.cancel)}
        icon={<CircleX size={14} aria-hidden />}
      >
        Huỷ lớp
      </GlassButton>
    </>
  );
}
