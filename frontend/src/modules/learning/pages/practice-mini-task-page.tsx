import { RequirePermission } from "@/entities/permission";
import { usePracticeMiniTaskController } from "@/modules/learning/hooks/use-practice-mini-task-controller";
import { AttemptResultBanner } from "@/modules/learning/ui/attempt-result-banner";
import { PracticeQuestion } from "@/modules/learning/ui/practice-question";
import type { PracticeMiniTask } from "@/entities/assessment";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ArrowLeft, Send } from "lucide-react";
import { Link, useParams } from "react-router-dom";

export function PracticeMiniTaskPage() {
  const { miniTaskId = "" } = useParams<{ miniTaskId: string }>();
  const controller = usePracticeMiniTaskController(miniTaskId);
  const paper = controller.paper.data;
  const result = controller.result;

  return (
    <RequirePermission {...ACCESS_RULE.readLearning}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title={paper?.title ?? "Mini-task"}
          description={practiceDescription(paper)}
          actions={
            <Link to={APP_ROUTE.myAssessments}>
              <GlassButton variant="secondary" size="sm" icon={<ArrowLeft size={14} aria-hidden />}>Bài tập & luyện tập</GlassButton>
            </Link>
          }
        />
        {controller.paper.isError ? <ErrorNotice error={controller.paper.error} /> : null}
        {controller.paper.isPending ? <Skeleton className="h-48" /> : null}
        {result ? <AttemptResultBanner result={result} onRetry={controller.retry} /> : null}
        {paper ? (
          <div className="flex flex-col gap-3">
            {controller.orderedQuestions.map((item, index) => (
              <PracticeQuestion
                key={item.question.id}
                index={index}
                item={item}
                selected={controller.answers[item.question.id]}
                review={controller.reviews.get(item.question.id)}
                onChoose={controller.choose}
              />
            ))}
          </div>
        ) : null}
        {controller.submitError ? <ErrorNotice error={controller.submitError} /> : null}
        {paper && !result ? (
          <div className="flex items-center justify-end gap-3">
            <span className="text-xs text-mist-400">Đã chọn {controller.answeredCount}/{controller.questionCount} câu</span>
            <GlassButton onClick={controller.submit} loading={controller.isSubmitting} icon={<Send size={14} aria-hidden />}>
              Nộp bài
            </GlassButton>
          </div>
        ) : null}
      </div>
    </RequirePermission>
  );
}

function practiceDescription(paper: PracticeMiniTask | undefined): string {
  const guide = "Chọn một phương án cho mỗi câu rồi nộp bài. Câu hỏi và phương án được xáo lại mỗi lượt, làm lại bao nhiêu lần cũng được.";
  if (!paper) {
    return guide;
  }
  const goal = `Đạt khi đúng từ ${paper.passPercent}% số câu.`;
  return paper.description ? `${paper.description} · ${goal}` : `${guide} ${goal}`;
}
