import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL, type EnrollmentSummary } from "@/entities/enrollment";
import { Can } from "@/entities/permission";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { formatter } from "@/shared/lib/format";
import { staggerDelay } from "@/shared/lib/motion";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { m } from "framer-motion";
import { CheckCircle2, LogOut } from "lucide-react";

export interface EnrollmentsTableProps {
  readonly rows: readonly EnrollmentSummary[];
  readonly onWithdraw: (enrollmentId: string) => void;
  readonly onComplete: (enrollmentId: string) => void;
  readonly isMutating: boolean;
}

export function EnrollmentsTable({ rows, onWithdraw, onComplete, isMutating }: EnrollmentsTableProps) {
  return (
    <ul className="flex flex-col gap-2">
      {rows.map((enrollment, index) => (
        <m.li
          key={enrollment.id}
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={staggerDelay(index)}
          className="glass grid grid-cols-1 items-center gap-3 rounded-2xl p-3 lg:grid-cols-[minmax(0,2fr)_auto_auto_auto]"
        >
          <div className="min-w-0">
            <p className="truncate text-sm text-mist-100">Học viên {enrollment.studentProfileId.slice(0, 8)}</p>
            <p className="truncate text-xs text-mist-500">Lớp {enrollment.classId.slice(0, 8)}</p>
          </div>
          <Badge tone={enrollment.status === ENROLLMENT_STATUS.active ? "positive" : "neutral"}>
            {ENROLLMENT_STATUS_LABEL[enrollment.status]}
          </Badge>
          <p className="text-xs text-mist-500">{formatter.dateTime(enrollment.enrolledAt)}</p>
          <EnrollmentRowActions
            enrollment={enrollment}
            onWithdraw={onWithdraw}
            onComplete={onComplete}
            isMutating={isMutating}
          />
        </m.li>
      ))}
    </ul>
  );
}

/** Hai nút chỉ có nghĩa khi ghi danh còn ACTIVE - tách ra để không nhét điều kiện vào JSX của bảng. */
function EnrollmentRowActions({
  enrollment,
  onWithdraw,
  onComplete,
  isMutating,
}: {
  readonly enrollment: EnrollmentSummary;
  readonly onWithdraw: (enrollmentId: string) => void;
  readonly onComplete: (enrollmentId: string) => void;
  readonly isMutating: boolean;
}) {
  if (enrollment.status !== ENROLLMENT_STATUS.active) {
    return <span className="text-xs text-mist-500">Không còn thao tác</span>;
  }
  return (
    <Can {...ACCESS_RULE.updateEnrollment}>
      <div className="flex gap-2 justify-self-start lg:justify-self-end">
        <GlassButton
          variant="secondary"
          size="sm"
          disabled={isMutating}
          onClick={() => onComplete(enrollment.id)}
          icon={<CheckCircle2 size={14} aria-hidden />}
        >
          Hoàn tất
        </GlassButton>
        <GlassButton
          variant="ghost"
          size="sm"
          disabled={isMutating}
          onClick={() => onWithdraw(enrollment.id)}
          icon={<LogOut size={14} aria-hidden />}
        >
          Rút
        </GlassButton>
      </div>
    </Can>
  );
}
