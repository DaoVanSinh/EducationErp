import type {
  FaqItemData,
  RoadmapStep,
  WhyChooseFeature,
} from "@/modules/landing/model/landing-schema";
import { Plus, Save, Trash2 } from "lucide-react";
import type { FormEvent } from "react";

interface AdminLandingContentEditorProps {
  readonly roadmapSteps: readonly RoadmapStep[];
  readonly whyChooseFeatures: readonly WhyChooseFeature[];
  readonly faqs: readonly FaqItemData[];
  readonly isSaving: boolean;
  readonly saveMessage: string | null;
  readonly onRoadmapChange: (index: number, field: keyof RoadmapStep, value: string) => void;
  readonly onRoadmapFeaturesTextChange: (index: number, rawText: string) => void;
  readonly onFeatureChange: (index: number, field: keyof WhyChooseFeature, value: string) => void;
  readonly onFaqChange: (index: number, field: keyof FaqItemData, value: string) => void;
  readonly onAddFaq: () => void;
  readonly onRemoveFaq: (index: number) => void;
  readonly onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}

const INPUT_SM = "rounded-lg border border-slate-300 px-2.5 py-1.5 text-xs text-slate-700";
const INPUT_TITLE = "w-full rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-bold text-slate-900";

export function AdminLandingContentEditor({
  roadmapSteps,
  whyChooseFeatures,
  faqs,
  isSaving,
  saveMessage,
  onRoadmapChange,
  onRoadmapFeaturesTextChange,
  onFeatureChange,
  onFaqChange,
  onAddFaq,
  onRemoveFaq,
  onSubmit,
}: AdminLandingContentEditorProps) {
  return (
    <form onSubmit={onSubmit} className="space-y-8">
      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <h3 className="text-base font-bold text-slate-900">
          1. Lộ trình đào tạo ({roadmapSteps.length} giai đoạn)
        </h3>
        <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2">
          {roadmapSteps.map((step, idx) => (
            <div key={step.id ?? step.step} className="space-y-3 rounded-xl border border-slate-200 p-4">
              <div className="flex items-center justify-between">
                <span className="rounded-lg bg-orange-100 px-2.5 py-1 text-xs font-bold text-orange-700">
                  Bước {step.step}
                </span>
                <input
                  type="text"
                  value={step.badgeText}
                  onChange={(e) => onRoadmapChange(idx, "badgeText", e.target.value)}
                  aria-label={`Nhãn bước ${step.step}`}
                  className={INPUT_SM}
                />
              </div>
              <input
                type="text"
                value={step.title}
                onChange={(e) => onRoadmapChange(idx, "title", e.target.value)}
                aria-label={`Tiêu đề bước ${step.step}`}
                className={INPUT_TITLE}
              />
              <div className="grid grid-cols-2 gap-2">
                <input
                  type="text"
                  value={step.duration}
                  onChange={(e) => onRoadmapChange(idx, "duration", e.target.value)}
                  placeholder="Thời lượng"
                  className={INPUT_SM}
                />
                <input
                  type="text"
                  value={step.target}
                  onChange={(e) => onRoadmapChange(idx, "target", e.target.value)}
                  placeholder="Mục tiêu đầu ra"
                  className={INPUT_SM}
                />
              </div>
              <textarea
                rows={3}
                value={step.features.join("\n")}
                onChange={(e) => onRoadmapFeaturesTextChange(idx, e.target.value)}
                aria-label={`Nội dung chi tiết bước ${step.step}`}
                className={`w-full ${INPUT_SM}`}
              />
            </div>
          ))}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <h3 className="text-base font-bold text-slate-900">
          2. Điểm khác biệt ({whyChooseFeatures.length} mục)
        </h3>
        <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
          {whyChooseFeatures.map((feat, idx) => (
            <div key={feat.id ?? feat.title} className="space-y-2.5 rounded-xl border border-slate-200 p-4">
              <input
                type="text"
                value={feat.subtitle}
                onChange={(e) => onFeatureChange(idx, "subtitle", e.target.value)}
                aria-label={`Nhãn phụ mục ${idx + 1}`}
                className="w-full rounded-lg border border-slate-300 px-2.5 py-1 text-xs font-semibold text-orange-700"
              />
              <input
                type="text"
                value={feat.title}
                onChange={(e) => onFeatureChange(idx, "title", e.target.value)}
                aria-label={`Tiêu đề mục ${idx + 1}`}
                className={INPUT_TITLE}
              />
              <textarea
                rows={2}
                value={feat.description}
                onChange={(e) => onFeatureChange(idx, "description", e.target.value)}
                aria-label={`Mô tả mục ${idx + 1}`}
                className={`w-full ${INPUT_SM}`}
              />
            </div>
          ))}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-bold text-slate-900">
            3. Câu hỏi thường gặp - FAQ ({faqs.length} câu)
          </h3>
          <button
            type="button"
            onClick={onAddFaq}
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-xl border border-orange-200 bg-orange-50 px-3.5 py-1.5 text-xs font-bold text-orange-700 hover:bg-orange-100"
          >
            <Plus className="h-3.5 w-3.5" />
            Thêm câu hỏi FAQ
          </button>
        </div>

        <div className="mt-4 space-y-3">
          {faqs.map((faq, idx) => (
            <div key={faq.id ?? idx} className="rounded-xl border border-slate-200 p-4">
              <div className="flex items-center justify-between gap-2">
                <input
                  type="text"
                  value={faq.question}
                  onChange={(e) => onFaqChange(idx, "question", e.target.value)}
                  aria-label={`Câu hỏi FAQ ${idx + 1}`}
                  className={INPUT_TITLE}
                />
                <button
                  type="button"
                  onClick={() => onRemoveFaq(idx)}
                  aria-label={`Xoá câu hỏi ${idx + 1}`}
                  className="shrink-0 cursor-pointer rounded-lg p-2 text-rose-600 hover:bg-rose-50"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
              <textarea
                rows={2}
                value={faq.answer}
                onChange={(e) => onFaqChange(idx, "answer", e.target.value)}
                aria-label={`Câu trả lời FAQ ${idx + 1}`}
                className={`mt-2 w-full ${INPUT_SM}`}
              />
            </div>
          ))}
        </div>
      </div>

      {saveMessage && (
        <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
          {saveMessage}
        </div>
      )}

      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isSaving}
          className="inline-flex cursor-pointer items-center gap-2 rounded-xl bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-5 py-2.5 text-sm font-semibold text-white shadow-xs hover:opacity-95 disabled:opacity-60"
        >
          <Save className="h-4 w-4" />
          {isSaving ? "Đang lưu nội dung..." : "Lưu toàn bộ nội dung Landing Page"}
        </button>
      </div>
    </form>
  );
}
