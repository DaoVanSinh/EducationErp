import type { LandingCourse } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";
import { m } from "framer-motion";
import { ArrowRight, BookOpen, Calendar, CheckCircle2, Layers } from "lucide-react";

interface LandingCourseCardProps {
  readonly course: LandingCourse;
  readonly index: number;
  readonly onOpenDetail: (course: LandingCourse) => void;
}

const ACCENT_PALETTES = [
  {
    badgeBg: "bg-[#fff7ed] text-[#ea580c] border-[#fed7aa]",
    topBar: "from-[#ff8c42] to-[#ff5e62]",
    iconBg: "bg-[#fff7ed] text-[#ea580c]",
  },
  {
    badgeBg: "bg-[#fff0f3] text-[#f04452] border-[#fecdd3]",
    topBar: "from-[#f04452] to-[#fb7185]",
    iconBg: "bg-[#fff0f3] text-[#f04452]",
  },
  {
    badgeBg: "bg-[#f0fdf4] text-[#059669] border-[#bbf7d0]",
    topBar: "from-[#10b981] to-[#34d399]",
    iconBg: "bg-[#f0fdf4] text-[#059669]",
  },
  {
    badgeBg: "bg-[#fffbeb] text-[#d97706] border-[#fde68a]",
    topBar: "from-[#f59e0b] to-[#fbbf24]",
    iconBg: "bg-[#fffbeb] text-[#d97706]",
  },
] as const;

export function LandingCourseCard({ course, index, onOpenDetail }: LandingCourseCardProps) {
  const palette = ACCENT_PALETTES[index % ACCENT_PALETTES.length] ?? ACCENT_PALETTES[0];
  const sessionLabel =
    course.standardSessionCount !== null ? `${course.standardSessionCount} buổi chuẩn` : "Lộ trình linh hoạt";
  const tuitionLabel =
    course.tuitionFee !== null ? formatter.currency(course.tuitionFee) : "Liên hệ tư vấn";

  return (
    <m.article
      initial={{ opacity: 0, y: 18 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ duration: 0.35, delay: (index % 3) * 0.06 }}
      onClick={() => onOpenDetail(course)}
      className="card-3d-hover group relative flex cursor-pointer flex-col justify-between overflow-hidden rounded-[24px] border border-[#ffedd5] bg-white p-6 shadow-[0_8px_28px_rgba(255,140,66,0.06)]"
    >
      <div className={`absolute inset-x-0 top-0 h-1.5 bg-gradient-to-r ${palette.topBar}`} />

      <div>
        <div className="mb-4 flex items-center justify-between gap-2">
          <span
            className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-[11px] font-bold tracking-wider uppercase ${palette.badgeBg}`}
          >
            <BookOpen className="h-3.5 w-3.5" />
            {course.code}
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-[#fffaf5] px-3 py-1 text-[11px] font-semibold text-[#475569]">
            <span
              className={`h-2 w-2 rounded-full ${
                course.openClassCount > 0 ? "bg-emerald-500" : "bg-amber-400"
              }`}
            />
            {course.openClassCount > 0 ? `${course.openClassCount} lớp đang mở` : "Sắp khai giảng"}
          </span>
        </div>

        <h3 className="text-[19px] font-extrabold leading-snug text-[#1e293b] transition-colors group-hover:text-[#ea580c]">
          {course.name}
        </h3>

        <p className="mt-2.5 line-clamp-2 text-[13.5px] leading-relaxed text-[#475569]">
          {course.description ??
            "Chương trình đào tạo bài bản với lộ trình rõ ràng, thực hành chuyên sâu và đánh giá định kỳ."}
        </p>

        <div className="mt-4 flex flex-wrap gap-2">
          <span className="inline-flex items-center gap-1.5 rounded-xl bg-[#fff7ed] px-3 py-1.5 text-[12px] font-semibold text-[#475569]">
            <Calendar className="h-3.5 w-3.5 text-[#ea580c]" />
            {sessionLabel}
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-xl bg-[#fff1f2] px-3 py-1.5 text-[12px] font-semibold text-[#475569]">
            <Layers className="h-3.5 w-3.5 text-[#ff5e62]" />
            {course.gradeColumns.length} đợt đánh giá định kỳ
          </span>
        </div>

        {course.gradeColumns.length > 0 && (
          <div className="mt-4 rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-3">
            <p className="mb-1.5 text-[11px] font-bold tracking-wider text-[#64748b] uppercase">
              Tiêu chí đánh giá khoá học
            </p>
            <div className="flex flex-wrap gap-1.5">
              {course.gradeColumns.slice(0, 3).map((col) => (
                <span
                  key={col.name}
                  className="inline-flex items-center gap-1 rounded-lg bg-white px-2.5 py-1 text-[11.5px] font-semibold text-[#475569] shadow-xs"
                >
                  <CheckCircle2 className="h-3 w-3 text-emerald-500" />
                  {col.name} ({col.weightPercent}%)
                </span>
              ))}
            </div>
          </div>
        )}
      </div>

      <div className="mt-6 flex items-center justify-between border-t border-[#ffedd5] pt-4">
        <div>
          <p className="text-[11px] font-semibold text-[#64748b] uppercase">Học phí niêm yết</p>
          <p className="text-[18px] font-black tracking-tight text-[#1e293b]">{tuitionLabel}</p>
        </div>

        <span className="inline-flex items-center gap-1.5 rounded-full bg-[#fff7ed] px-4 py-2 text-[12.5px] font-bold text-[#ea580c] transition-all group-hover:bg-gradient-to-r group-hover:from-[#ff8c42] group-hover:to-[#ff5e62] group-hover:text-white">
          Chi tiết & Lịch học
          <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-0.5" />
        </span>
      </div>
    </m.article>
  );
}
