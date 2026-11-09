import { useTeacherBranchHistory } from "@/entities/teacher";
import { formatter } from "@/shared/lib/format";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { Skeleton } from "@/shared/ui/skeleton";

/** Lịch sử chuyển chi nhánh chủ quản. Chỉ admin gốc chuyển được, nên phần lớn giáo viên không có dòng nào. */
export function TeacherBranchHistory({ profileId }: { readonly profileId: string }) {
  const history = useTeacherBranchHistory(profileId);

  if (history.isError) {
    return <ErrorNotice error={history.error} />;
  }
  if (history.isPending) {
    return <Skeleton className="h-10" />;
  }
  if (history.data.length === 0) {
    return <p className="text-xs text-mist-500">Chưa từng chuyển chi nhánh.</p>;
  }
  return (
    <ul className="flex flex-col gap-1">
      {history.data.map((change) => (
        <li key={change.changedAt} className="text-sm text-mist-100">
          {formatter.date(change.changedAt)}: {change.fromBranchName ?? "Chưa thuộc chi nhánh"} →{" "}
          {change.toBranchName ?? "Chưa thuộc chi nhánh"}
        </li>
      ))}
    </ul>
  );
}
