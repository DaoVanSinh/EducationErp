import { CLASS_STATUS, CLASS_STATUS_LABEL, type ClassStatus } from "@/entities/class/model/class-schema";
import { Badge, type BadgeProps } from "@/shared/ui/badge";

const STATUS_TONE: Record<ClassStatus, NonNullable<BadgeProps["tone"]>> = {
  [CLASS_STATUS.enrolling]: "warning",
  [CLASS_STATUS.inProgress]: "positive",
  [CLASS_STATUS.completed]: "neutral",
  [CLASS_STATUS.cancelled]: "danger",
};

/** Nhãn trạng thái lớp dùng chung cho màn quản trị, cổng giảng dạy và cổng học tập. */
export function ClassStatusBadge({ status }: { readonly status: ClassStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{CLASS_STATUS_LABEL[status]}</Badge>;
}
