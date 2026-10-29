import { RequirePermission } from "@/entities/permission";
import { usePracticeMiniTaskController } from "@/modules/learning/hooks/use-practice-mini-task-controller";
import { PracticeQuestion } from "@/modules/learning/ui/practice-question";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ACCESS_RULE } from "@/shared/constants/permissions";
import { ErrorNotice } from "@/shared/ui/error-notice";
import { GlassButton } from "@/shared/ui/glass-button";
import { GlassPanel } from "@/shared/ui/glass-panel";
import { PageHeader } from "@/shared/ui/page-header";
import { Skeleton } from "@/shared/ui/skeleton";
import { ArrowLeft, RotateCcw, Send } from "lucide-react";
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
          description={paper?.description ?? "Chọn một phương án cho mỗi câu rồi nộp bài. Bạn có thể làm lại nhiều lần."}
          actions={
            <Link to={APP_ROUTE.myAssessments}>
              <GlassButton variant="secondary" size="sm" icon={<ArrowLeft size={14} aria-hidden />}>Bài tập & luyện tập</GlassButton>
            </Link>
          }
        />
        {controller.paper.isError ? <ErrorNotice error={controller.paper.error} /> : null}
        {controller.paper.isPending ? <Skeleton className="h-48" /> : null}
        {result ? (
          <GlassPanel className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-base font-semibold text-mist-100">
              Kết quả: {result.correctCount}/{result.totalQuestions} câu đúng ({result.scorePercent}%)
            </p>
            <GlassButton size="sm" onClick={controller.retry} icon={<RotateCcw size={14} aria-hidden />}>Làm lại</GlassButton>
          </GlassPanel>
        ) : null}
        {paper ? (
          <div className="flex flex-col gap-3">
            {paper.questions.map((question, index) => (
              <PracticeQuestion
                key={question.id}
                index={index}
                question={question}
                selected={controller.answers[question.id]}
                review={result?.results[index]}
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
