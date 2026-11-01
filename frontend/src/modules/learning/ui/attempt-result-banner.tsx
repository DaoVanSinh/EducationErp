import type { AttemptResult } from "@/entities/assessment";
import { Badge } from "@/shared/ui/badge";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { RotateCcw } from "lucide-react";

export interface AttemptResultBannerProps {
  readonly result: AttemptResult;
  readonly onRetry: () => void;
}

/** Kết quả một lượt làm: số câu đúng, Đạt/Chưa đạt so với ngưỡng, nút làm lại. */
export function AttemptResultBanner({ result, onRetry }: AttemptResultBannerProps) {
  return (
    <GlassPanel className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex flex-col gap-1" role="status">
        <p className="text-base font-semibold text-mist-100">
          Kết quả: {result.correctCount}/{result.totalQuestions} câu đúng ({result.scorePercent}%)
        </p>
        <p className="text-xs text-mist-400">
          {result.passed
            ? "Bạn đã đạt mini-task này. Xem lại lời giải bên dưới để nhớ lâu hơn."
            : `Cần đúng từ ${result.passPercent}% để đạt. Xem lời giải các câu sai rồi làm lại nhé.`}
        </p>
      </div>
      <div className="flex items-center gap-2">
        <Badge tone={result.passed ? "positive" : "warning"}>{result.passed ? "Đạt" : "Chưa đạt"}</Badge>
        <GlassButton size="sm" onClick={onRetry} icon={<RotateCcw size={14} aria-hidden />}>Làm lại</GlassButton>
      </div>
    </GlassPanel>
  );
}
