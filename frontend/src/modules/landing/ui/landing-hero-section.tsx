import { useSakuraCanvas } from "@/modules/landing/hooks/use-sakura-canvas";
import {
  LANDING_SECTION_ID,
  type LandingCenterInfo,
  type LandingCourse,
  type LandingStats,
} from "@/modules/landing/model/landing-schema";
import { LandingHeroVisual } from "@/modules/landing/ui/landing-hero-visual";
import { m } from "framer-motion";
import {
  Award,
  BookOpen,
  Building2,
  CalendarCheck2,
  FileCheck2,
  GraduationCap,
  Headphones,
  Lightbulb,
  Search,
  Users,
} from "lucide-react";

export interface LandingHeroSectionProps {
  readonly centerInfo: LandingCenterInfo;
  readonly stats: LandingStats;
  readonly featuredCourse?: LandingCourse;
  readonly onConsultClick: () => void;
  readonly onScrollToSection: (sectionId: string) => void;
}

const VALUE_PILLARS = [
  { title: "TƯ VẤN LỘ TRÌNH 1:1", subtitle: "Cá nhân hoá theo năng lực", icon: Headphones, tone: "border-[#fed7aa] bg-[#fff7ed] text-[#ea580c]" },
  { title: "CHƯƠNG TRÌNH CHUẨN HOÁ", subtitle: "Thực tiễn, bám sát mục tiêu", icon: GraduationCap, tone: "border-[#fecdd3] bg-[#fff1f2] text-[#ff5e62]" },
  { title: "ĐÁNH GIÁ ĐỊNH KỲ", subtitle: "Theo sát tiến độ từng buổi", icon: FileCheck2, tone: "border-[#fed7aa] bg-[#fff7ed] text-[#ea580c]" },
  { title: "CAM KẾT ĐẦU RA", subtitle: "Đồng hành suốt khoá học", icon: Award, tone: "border-[#fecdd3] bg-[#fff1f2] text-[#ff5e62]" },
] as const;

export function LandingHeroSection({
  centerInfo,
  stats,
  featuredCourse,
  onConsultClick,
  onScrollToSection,
}: LandingHeroSectionProps) {
  const { canvasRef } = useSakuraCanvas();

  const statItems = [
    { label: "Khoá đào tạo", value: stats.activeCourseCount, icon: BookOpen, tone: "bg-[#fff7ed] text-[#ea580c]" },
    { label: "Lớp đang mở", value: stats.activeClassCount, icon: CalendarCheck2, tone: "bg-[#fff1f2] text-[#ff5e62]" },
    { label: "Giảng viên", value: stats.activeTeacherCount, icon: Users, tone: "bg-[#fff7ed] text-[#ea580c]" },
    { label: "Cơ sở đào tạo", value: stats.activeBranchCount, icon: Building2, tone: "bg-[#fff1f2] text-[#ff5e62]" },
  ] as const;

  return (
    <section className="relative overflow-hidden bg-white pt-9 pb-12">
      <canvas ref={canvasRef} aria-hidden className="pointer-events-none absolute inset-0 z-0 h-full w-full" />

      <div className="relative z-10 mx-auto max-w-[1380px] px-4 sm:px-8 lg:px-14">
        <div className="grid items-center gap-10 lg:grid-cols-[1.12fr_1fr]">
          <div className="max-w-[640px]">
            <m.div
              initial={{ opacity: 0, y: -10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.65 }}
              className="mb-5 inline-flex items-center gap-2 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-4 py-1.5 text-xs font-bold tracking-wide text-[#ea580c] shadow-[0_2px_8px_rgba(255,140,66,0.12)]"
            >
              <Lightbulb size={14} className="text-[#ea580c]" aria-hidden />
              <span>{centerInfo.heroBadge}</span>
            </m.div>

            <m.h1
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.7, delay: 0.06 }}
              className="mb-2.5 text-4xl leading-[1.1] font-extrabold tracking-tight text-[#1e293b] sm:text-5xl lg:text-[52px]"
            >
              {centerInfo.heroTitle}
            </m.h1>

            <m.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.75, delay: 0.12 }}
              className="hero-script mb-4 bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] bg-clip-text text-2xl font-bold text-transparent sm:text-3xl lg:text-[38px]"
            >
              {centerInfo.heroHighlight}
            </m.div>

            <m.p
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.75, delay: 0.18 }}
              className="mb-8 max-w-[540px] text-base leading-relaxed text-[#475569]"
            >
              {centerInfo.heroDescription}
            </m.p>

            <m.div
              initial={{ opacity: 0, y: 16 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.75, delay: 0.24 }}
              className="mb-9 flex flex-wrap items-center gap-4"
            >
              <button
                type="button"
                onClick={onConsultClick}
                className="inline-flex cursor-pointer items-center gap-2.5 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-7 py-3.5 text-[14.5px] font-bold text-white shadow-[0_6px_20px_-2px_rgba(255,140,66,0.35)] transition-all hover:-translate-y-0.5 hover:opacity-95"
              >
                <Headphones size={17} aria-hidden />
                <span>Tư vấn lộ trình miễn phí</span>
              </button>

              <button
                type="button"
                onClick={() => onScrollToSection(LANDING_SECTION_ID.courses)}
                className="inline-flex cursor-pointer items-center gap-2 rounded-full border border-[#fed7aa] bg-white px-7 py-3.5 text-[14.5px] font-semibold text-[#1e293b] transition-all hover:-translate-y-0.5 hover:border-[#ff8c42] hover:bg-[#fff7ed] hover:text-[#ea580c]"
              >
                <Search size={16} className="text-[#ea580c]" aria-hidden />
                <span>Khám phá các khoá học</span>
              </button>
            </m.div>

            <div className="grid grid-cols-2 gap-4 border-t border-[#ffedd5] pt-6 sm:grid-cols-4">
              {statItems.map((item) => {
                const Icon = item.icon;
                return (
                  <div key={item.label} className="flex items-center gap-2.5">
                    <div className={`flex size-9 shrink-0 items-center justify-center rounded-xl ${item.tone}`}>
                      <Icon size={18} aria-hidden />
                    </div>
                    <div>
                      <div className="text-base font-extrabold text-[#1e293b]">{item.value}</div>
                      <div className="text-[11.5px] font-medium text-[#64748b]">{item.label}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <LandingHeroVisual
            stats={stats}
            featuredCourse={featuredCourse}
            onExploreCourses={() => onScrollToSection(LANDING_SECTION_ID.courses)}
          />
        </div>

        {/* 4-Pillar Floating Value Proposition Bar */}
        <m.div
          initial={{ opacity: 0, y: 24 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.75, delay: 0.35 }}
          className="mt-11 grid grid-cols-1 gap-6 rounded-[24px] border border-[#ffedd5] bg-white px-6 py-5 shadow-[0_12px_32px_-4px_rgba(255,140,66,0.1),0_2px_8px_rgba(15,23,42,0.03)] sm:grid-cols-2 lg:grid-cols-4 lg:px-8"
        >
          {VALUE_PILLARS.map((pillar) => {
            const Icon = pillar.icon;
            return (
              <div key={pillar.title} className="flex items-center gap-4">
                <div className={`flex size-[46px] shrink-0 items-center justify-center rounded-[14px] border ${pillar.tone}`}>
                  <Icon size={22} aria-hidden />
                </div>
                <div>
                  <div className="text-[13px] font-extrabold tracking-wide text-[#1e293b] uppercase">
                    {pillar.title}
                  </div>
                  <div className="mt-0.5 text-[12.5px] font-medium text-[#64748b]">{pillar.subtitle}</div>
                </div>
              </div>
            );
          })}
        </m.div>
      </div>
    </section>
  );
}
