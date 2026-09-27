import { ApiError } from "@/shared/api/api-error";
import { m } from "framer-motion";
import { TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

export interface ErrorNoticeProps {
  readonly error: unknown;
  readonly action?: ReactNode;
}

/**
 * Hiển thị lỗi bằng đúng câu backend gửi kèm (ProblemDetail.detail đã là tiếng Việt), và liệt kê lỗi
 * từng ô khi đó là lỗi kiểm tra dữ liệu nhưng form không có ô tương ứng để gắn vào.
 */
export function ErrorNotice({ error, action }: ErrorNoticeProps) {
  if (error === null || error === undefined) {
    return null;
  }
  const issues = error instanceof ApiError ? error.fieldIssues : [];

  return (
    <m.div
      initial={{ opacity: 0, y: -6 }}
      animate={{ opacity: 1, y: 0 }}
      role="alert"
      className="flex items-start gap-3 rounded-2xl border border-rose-400/30 bg-rose-400/10 px-4 py-3"
    >
      <TriangleAlert size={18} className="mt-0.5 shrink-0 text-rose-400" aria-hidden />
      <div className="flex-1 text-sm text-mist-200">
        <p>{ApiError.messageOf(error)}</p>
        {issues.length > 0 ? (
          <ul className="mt-1 list-inside list-disc text-xs text-mist-400">
            {issues.map((issue) => (
              <li key={issue.field}>{issue.message}</li>
            ))}
          </ul>
        ) : null}
        {action ? <div className="mt-3">{action}</div> : null}
      </div>
    </m.div>
  );
}
