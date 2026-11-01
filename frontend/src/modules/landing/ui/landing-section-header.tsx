import { m } from "framer-motion";

export interface LandingSectionHeaderProps {
  readonly badge: string;
  readonly title: string;
  readonly highlight: string;
  readonly subtitle?: string;
}

export function LandingSectionHeader({
  badge,
  title,
  highlight,
  subtitle,
}: LandingSectionHeaderProps) {
  return (
    <div className="relative mx-auto mb-11 max-w-[760px] text-center">
      <div
        aria-hidden
        className="pointer-events-none absolute -top-4 left-1/2 z-0 h-[120px] w-[340px] -translate-x-1/2 blur-2xl"
        style={{
          background:
            "radial-gradient(ellipse, rgba(255, 140, 66, 0.2) 0%, rgba(255, 94, 98, 0.12) 50%, transparent 80%)",
        }}
      />

      <m.div
        initial={{ opacity: 0, y: 10 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.55 }}
        className="relative z-10 mb-3 inline-flex items-center gap-2 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3.5 py-1 text-xs font-bold tracking-wide text-[#ea580c]"
      >
        <span>{badge}</span>
      </m.div>

      <m.h2
        initial={{ opacity: 0, y: 14 }}
        whileInView={{ opacity: 1, y: 0 }}
        viewport={{ once: true }}
        transition={{ duration: 0.62, delay: 0.06 }}
        className="relative z-10 mb-3 text-2xl font-extrabold tracking-tight text-[#1e293b] sm:text-3xl lg:text-4xl"
      >
        {title} <span className="text-[#ea580c]">{highlight}</span>
      </m.h2>

      {subtitle && (
        <m.p
          initial={{ opacity: 0, y: 12 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          transition={{ duration: 0.68, delay: 0.12 }}
          className="relative z-10 text-[15.5px] leading-relaxed text-[#475569]"
        >
          {subtitle}
        </m.p>
      )}
    </div>
  );
}
