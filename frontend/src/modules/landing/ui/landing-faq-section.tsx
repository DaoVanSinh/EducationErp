import { LANDING_SECTION_ID, type FaqItemData } from "@/modules/landing/model/landing-schema";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { AnimatePresence, m } from "framer-motion";
import { ChevronDown, HelpCircle } from "lucide-react";

interface LandingFaqSectionProps {
  readonly faqItems: readonly FaqItemData[];
  readonly openFaqIndex: number | null;
  readonly onToggleFaq: (index: number) => void;
}

export function LandingFaqSection({
  faqItems,
  openFaqIndex,
  onToggleFaq,
}: LandingFaqSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.faq} className="bg-[#fffaf5] py-20 lg:py-24">
      <div className="mx-auto max-w-[920px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="GIẢI ĐÁP THẮC MẮC TUYỂN SINH"
          title="Câu hỏi"
          highlight="Thường gặp về Khoá học & Học phí"
          subtitle="Giải đáp nhanh các thắc mắc về lịch khai giảng, chính sách ưu đãi học phí Combo và quy trình đăng ký học."
        />

        <div className="space-y-3.5">
          {faqItems.map((item, idx) => {
            const isOpen = openFaqIndex === idx;
            return (
              <div
                key={item.id ?? item.question}
                className="overflow-hidden rounded-[22px] border border-[#ffedd5] bg-white shadow-[0_4px_16px_rgba(255,140,66,0.05)]"
              >
                <button
                  type="button"
                  onClick={() => onToggleFaq(idx)}
                  aria-expanded={isOpen}
                  className="flex w-full cursor-pointer items-center justify-between gap-4 px-6 py-5 text-left"
                >
                  <span className="flex items-center gap-3 text-[15.5px] font-extrabold text-[#1e293b]">
                    <HelpCircle className="h-5 w-5 shrink-0 text-[#ea580c]" />
                    {item.question}
                  </span>
                  <ChevronDown
                    className={`h-5 w-5 shrink-0 text-[#94a3b8] transition-transform duration-200 ${
                      isOpen ? "rotate-180 text-[#ea580c]" : ""
                    }`}
                  />
                </button>

                <AnimatePresence initial={false}>
                  {isOpen && (
                    <m.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: "auto", opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      transition={{ duration: 0.22 }}
                    >
                      <div className="border-t border-[#ffedd5] px-6 pt-3 pb-5 text-[14px] leading-relaxed text-[#475569]">
                        {item.answer}
                      </div>
                    </m.div>
                  )}
                </AnimatePresence>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
