import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_LABEL, type StudentProgressRow } from "@/entities/academic";
import { formatter } from "@/shared/lib/format";
import { Badge } from "@/shared/ui/badge";

export interface ClassProgressTableProps {
  readonly rows: readonly StudentProgressRow[];
}

/**
 * `progressPercent` null = khóa chưa cấu hình tổng số buổi chuẩn (spec mục 5) - phải nói thành chữ,
 * không hiển thị 0% (người xem sẽ hiểu sai là học viên chưa học buổi nào).
 */
function ProgressCell({ row }: { readonly row: StudentProgressRow }) {
  if (row.progressPercent === null) {
    return <Badge tone="neutral">Chưa cấu hình tổng số buổi chuẩn</Badge>;
  }
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-24 overflow-hidden rounded-full bg-white/10">
        <div className="h-full rounded-full bg-sky-400" style={{ width: `${row.progressPercent}%` }} />
      </div>
      <span className="text-sm text-mist-100">{row.progressPercent}%</span>
    </div>
  );
}

/** Học viên đã rút: tiến trình đóng băng tại mốc rút, nên phải nói rõ mốc đó ngay trên dòng. */
function StatusCell({ row }: { readonly row: StudentProgressRow }) {
  if (row.status !== ENROLLMENT_STATUS.withdrawn || row.withdrawnAt === null) {
    return <span className="text-xs text-mist-400">{ENROLLMENT_STATUS_LABEL[row.status]}</span>;
  }
  return (
    <span className="text-xs text-mist-400">
      {ENROLLMENT_STATUS_LABEL[row.status]} · {formatter.dateTime(row.withdrawnAt)}
    </span>
  );
}

export function ClassProgressTable({ rows }: ClassProgressTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[640px] text-left">
        <thead>
          <tr className="text-xs text-mist-500">
            <th scope="col" className="p-2 font-normal">Học viên</th>
            <th scope="col" className="p-2 font-normal">Trạng thái</th>
            <th scope="col" className="p-2 font-normal">Ngày ghi danh</th>
            <th scope="col" className="p-2 font-normal">Số buổi đã học</th>
            <th scope="col" className="p-2 font-normal">Tiến trình</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.enrollmentId} className="glass rounded-2xl">
              <td className="p-2 text-sm text-mist-100">{row.studentName ?? "—"}</td>
              <td className="p-2">
                <StatusCell row={row} />
              </td>
              <td className="p-2 text-xs text-mist-400">{formatter.dateTime(row.enrolledAt)}</td>
              <td className="p-2 text-sm text-mist-100">
                {row.elapsedSessions}
                {row.standardSessionCount === null ? "" : ` / ${row.standardSessionCount}`}
              </td>
              <td className="p-2">
                <ProgressCell row={row} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
