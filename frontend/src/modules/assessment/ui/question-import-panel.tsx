import { useQuestionImport } from "@/modules/assessment/hooks/use-question-import";
import type { QuestionDraft } from "@/modules/assessment/model/assessment-rules";
import { GlassButton } from "@/shared/ui/glass-button";
import { ClipboardPaste, FileText } from "lucide-react";

export interface QuestionImportPanelProps {
  readonly onImport: (questions: readonly QuestionDraft[]) => void;
}

/** Dán cả bộ câu hỏi (định dạng Aiken của Moodle) để khỏi gõ từng ô - nhanh khi đã có đề sẵn trong Word. */
export function QuestionImportPanel({ onImport }: QuestionImportPanelProps) {
  const panel = useQuestionImport(onImport);

  if (!panel.open) {
    return (
      <div>
        <GlassButton variant="secondary" size="sm" onClick={panel.toggle} icon={<ClipboardPaste size={14} aria-hidden />}>
          Nhập nhanh từ văn bản
        </GlassButton>
      </div>
    );
  }

  return (
    <section className="glass flex flex-col gap-2 rounded-2xl p-3" aria-label="Nhập nhanh câu hỏi">
      <p className="text-xs text-mist-400">
        Mỗi câu một khối, cách nhau bằng một dòng trống. Phương án bắt đầu bằng A. B. C. ...; đáp án đúng ghi ở dòng
        &quot;ĐÁP ÁN: A&quot; hoặc đánh dấu * trước chữ cái. Dòng &quot;GIẢI THÍCH: ...&quot; là tuỳ chọn.
      </p>
      <textarea
        aria-label="Văn bản câu hỏi"
        rows={8}
        value={panel.text}
        onChange={(event) => panel.setText(event.target.value)}
        placeholder="Dán câu hỏi vào đây"
        className="w-full rounded-2xl border border-slate-200/90 bg-white/75 p-3 font-mono text-xs text-slate-800 focus:border-orange-400 focus:outline-none focus:ring-2 focus:ring-orange-300/30"
      />
      {panel.importedCount !== null ? (
        <p className="text-xs text-emerald-700" role="status">Đã thêm {panel.importedCount} câu vào danh sách.</p>
      ) : null}
      {panel.errors.length > 0 ? (
        <ul className="text-xs text-rose-600" role="alert">
          {panel.errors.map((error) => <li key={error}>{error}</li>)}
        </ul>
      ) : null}
      <div className="flex flex-wrap gap-2">
        <GlassButton size="sm" onClick={panel.runImport} disabled={panel.text.trim() === ""}>Thêm vào danh sách</GlassButton>
        <GlassButton variant="ghost" size="sm" onClick={panel.fillSample} icon={<FileText size={14} aria-hidden />}>
          Xem mẫu
        </GlassButton>
        <GlassButton variant="ghost" size="sm" onClick={panel.toggle}>Đóng</GlassButton>
      </div>
    </section>
  );
}
