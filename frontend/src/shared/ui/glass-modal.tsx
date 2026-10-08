import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING, RISE_IN } from "@/shared/lib/motion";
import { GlassButton } from "@/shared/ui/glass-button";
import { AnimatePresence, m } from "framer-motion";
import { X } from "lucide-react";
import { type ReactNode, useEffect } from "react";

export interface GlassModalProps {
  readonly open: boolean;
  readonly title: string;
  readonly description?: string;
  readonly icon?: ReactNode;
  readonly onClose: () => void;
  readonly children: ReactNode;
  readonly footer?: ReactNode;
  readonly className?: string;
}

export function GlassModal({
  open,
  title,
  description,
  icon,
  onClose,
  children,
  footer,
  className,
}: GlassModalProps) {
  useEffect(() => {
    if (!open) {
      return;
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open ? (
        <m.div
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          initial="hidden"
          animate="visible"
          exit="hidden"
        >
          <m.button
            type="button"
            aria-label="Đóng"
            onClick={onClose}
            className="absolute inset-0 cursor-default bg-slate-900/40 backdrop-blur-md"
            variants={{ hidden: { opacity: 0 }, visible: { opacity: 1 } }}
          />
          <m.div
            role="dialog"
            aria-modal="true"
            aria-label={title}
            variants={RISE_IN}
            transition={MOTION_SPRING}
            className={cx(
              "glass-raised relative z-10 flex max-h-[90vh] w-full max-w-xl flex-col gap-5 overflow-y-auto rounded-3xl p-6 shadow-2xl border border-white/90 bg-white/90 backdrop-blur-2xl",
              className,
            )}
          >
            <header className="flex items-start justify-between gap-4 border-b border-slate-100/90 pb-4">
              <div className="flex items-center gap-3.5">
                {icon ? (
                  <div className="flex size-11 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-[#FF8C42] to-[#FF5E62] text-white shadow-md shadow-orange-500/25">
                    {icon}
                  </div>
                ) : null}
                <div>
                  <h2 className="text-lg font-bold text-slate-900">{title}</h2>
                  {description ? <p className="mt-0.5 text-xs text-slate-500">{description}</p> : null}
                </div>
              </div>
              <GlassButton variant="ghost" size="sm" onClick={onClose} aria-label="Đóng">
                <X size={16} aria-hidden />
              </GlassButton>
            </header>
            <div className="flex flex-col gap-4">{children}</div>
            {footer ? <footer className="flex justify-end gap-2 border-t border-slate-100/90 pt-4">{footer}</footer> : null}
          </m.div>
        </m.div>
      ) : null}
    </AnimatePresence>
  );
}
