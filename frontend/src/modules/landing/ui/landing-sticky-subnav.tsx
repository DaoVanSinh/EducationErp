import { STICKY_SUB_NAV_ITEMS } from "@/modules/landing/model/landing-schema";
import { cx } from "@/shared/lib/class-names";
import { Sparkles } from "lucide-react";

export interface LandingStickySubnavProps {
  readonly activeSection: string;
  readonly onScrollToSection: (sectionId: string) => void;
  readonly onConsultClick: () => void;
}

export function LandingStickySubnav({
  activeSection,
  onScrollToSection,
  onConsultClick,
}: LandingStickySubnavProps) {
  return (
    <div className="sticky top-[74px] z-30 border-b border-[#ffedd5] bg-white/95 shadow-[0_4px_16px_rgba(255,140,66,0.06)] backdrop-blur-md">
      <div className="mx-auto flex h-[52px] max-w-[1280px] items-center justify-between gap-4 overflow-x-auto px-4 sm:px-6">
        <div className="flex items-center gap-1">
          {STICKY_SUB_NAV_ITEMS.map((item) => {
            const isActive = activeSection === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => onScrollToSection(item.id)}
                className={cx(
                  "cursor-pointer rounded-t-md border-b-2 px-3.5 py-3 text-[13.5px] whitespace-nowrap transition-all",
                  isActive
                    ? "border-[#ff8c42] bg-[#fff7ed] font-extrabold text-[#ea580c]"
                    : "border-transparent font-semibold text-[#475569] hover:text-[#ea580c]",
                )}
              >
                {item.label}
              </button>
            );
          })}
        </div>

        <button
          type="button"
          onClick={onConsultClick}
          className="hidden shrink-0 cursor-pointer items-center gap-2 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3 py-1 transition-transform hover:scale-105 md:inline-flex"
        >
          <span className="relative flex size-6 items-center justify-center rounded-full bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-white">
            <Sparkles size={13} aria-hidden />
            <span className="absolute -right-0.5 -bottom-0.5 size-2 rounded-full border border-white bg-emerald-500" />
          </span>
          <span className="text-[11.5px] font-bold text-[#ea580c]">Tư vấn viên trực tuyến</span>
        </button>
      </div>
    </div>
  );
}
