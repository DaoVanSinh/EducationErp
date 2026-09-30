import { formatter } from "@/shared/lib/format";
import { GlassButton } from "@/shared/ui/glass-button";
import { ChevronLeft, ChevronRight } from "lucide-react";

export interface PaginationProps {
  /** Trang hiện tại, đánh số từ 0 như Pageable của Spring - đổi gốc ở đây sẽ lệch với API. */
  readonly page: number;
  readonly totalPages: number;
  readonly totalItems: number;
  readonly onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, totalItems, onPageChange }: PaginationProps) {
  const isFirst = page <= 0;
  const isLast = page >= totalPages - 1;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-xs font-medium text-slate-500">
        Trang {formatter.count(page + 1)}/{formatter.count(Math.max(totalPages, 1))} ·{" "}
        {formatter.count(totalItems)} tài khoản
      </p>
      <div className="flex items-center gap-2">
        <GlassButton
          variant="secondary"
          size="sm"
          disabled={isFirst}
          onClick={() => onPageChange(page - 1)}
          icon={<ChevronLeft size={14} aria-hidden />}
        >
          Trước
        </GlassButton>
        <GlassButton variant="secondary" size="sm" disabled={isLast} onClick={() => onPageChange(page + 1)}>
          Sau
          <ChevronRight size={14} aria-hidden />
        </GlassButton>
      </div>
    </div>
  );
}
