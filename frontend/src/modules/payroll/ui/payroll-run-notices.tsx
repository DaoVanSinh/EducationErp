import type { MissingPayTerms } from "@/entities/payroll";
import { TriangleAlert } from "lucide-react";
import type { ReactNode } from "react";

function WarningBox({ children }: { readonly children: ReactNode }) {
  return (
    <div role="status" className="flex items-start gap-3 rounded-2xl border border-amber-200/90 bg-amber-50/90 px-4 py-3">
      <TriangleAlert size={18} className="mt-0.5 shrink-0 text-amber-600" aria-hidden />
      <div className="flex-1 text-sm text-amber-900">{children}</div>
    </div>
  );
}

export function RejectionNotice({ reason }: { readonly reason: string }) {
  return (
    <WarningBox>
      <p className="font-medium">Kỳ lương bị từ chối, cần sửa rồi gửi duyệt lại</p>
      <p>{reason}</p>
    </WarningBox>
  );
}

export function SubmitBlockedNotice({ reason }: { readonly reason: string }) {
  return (
    <WarningBox>
      <p>{reason}</p>
    </WarningBox>
  );
}

/** Người đã dạy mà chưa có mức lương sẽ bị bỏ sót lương, nên liệt kê tên để kế toán xử lý ngay. */
export function MissingPayTermsNotice({ items }: { readonly items: readonly MissingPayTerms[] }) {
  return (
    <WarningBox>
      <p className="font-medium">Giáo viên chưa thiết lập lương, chưa có phiếu trong kỳ này</p>
      <ul className="mt-1 list-disc pl-4">
        {items.map((item) => (
          <li key={item.accountId}>
            {item.fullName ?? "Chưa rõ tên"}
            {item.sessionCount > 0 ? `: đã dạy ${item.sessionCount} buổi` : ": chưa có buổi dạy nào"}
          </li>
        ))}
      </ul>
      <p className="mt-1">
        Kỳ lương chưa gửi duyệt được khi còn người đã dạy mà chưa có lương. Thiết lập ở trang chi tiết giáo viên, tab
        Lương, rồi bấm Tính lại.
      </p>
    </WarningBox>
  );
}
