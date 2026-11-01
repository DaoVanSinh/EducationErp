import {
  LANDING_SECTION_ID,
  type LandingMorphVariant,
  type WhyChooseFeature,
} from "@/modules/landing/model/landing-schema";
import { LandingMorphIcon } from "@/modules/landing/ui/landing-morph-icon";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { m } from "framer-motion";
import { Award, Clock, GraduationCap, ShieldCheck, Sparkles, Wallet } from "lucide-react";

interface LandingWhyUsSectionProps {
  readonly features: readonly WhyChooseFeature[];
}

function resolveFeatureIcon(iconKey: string) {
  switch (iconKey) {
    case "wallet":
      return Wallet;
    case "graduation":
      return GraduationCap;
    case "sparkles":
      return Sparkles;
    case "shield":
      return ShieldCheck;
    case "clock":
      return Clock;
    default:
      return Award;
  }
}

function resolveMorphVariant(variant: string): LandingMorphVariant {
  if (
    variant === "blue" ||
    variant === "rose" ||
    variant === "emerald" ||
    variant === "amber" ||
    variant === "purple" ||
    variant === "cyan"
  ) {
    return variant;
  }
  return "blue";
}

export function LandingWhyUsSection({ features }: LandingWhyUsSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.whyUs} className="bg-white py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="GIÁ TRỊ ĐÀO TẠO KHÁC BIỆT"
          title="Vì sao học viên tin chọn"
          highlight="Chương trình đào tạo của chúng tôi"
          subtitle="Sự kết hợp giữa đội ngũ giảng viên tận tâm, giáo trình thực tiễn và lộ trình học tập cá nhân hoá."
        />

        <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
          {features.map((feature, index) => {
            const Icon = resolveFeatureIcon(feature.iconKey);
            const variant = resolveMorphVariant(feature.variant);
            return (
              <m.article
                key={feature.id ?? feature.title}
                initial={{ opacity: 0, y: 18 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: (index % 3) * 0.06 }}
                className="card-3d-hover rounded-[24px] border border-[#ffedd5] bg-[#fffaf5] p-6 transition-colors hover:bg-white"
              >
                <div className="mb-4 flex items-center gap-3.5">
                  <LandingMorphIcon icon={Icon} variant={variant} />
                  <span className="text-[11.5px] font-extrabold tracking-wider text-[#ea580c] uppercase">
                    {feature.subtitle}
                  </span>
                </div>

                <h3 className="text-[18px] font-extrabold text-[#1e293b]">{feature.title}</h3>
                <p className="mt-2 text-[13.5px] leading-relaxed text-[#475569]">
                  {feature.description}
                </p>
              </m.article>
            );
          })}
        </div>
      </div>
    </section>
  );
}
