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
  readonly onClose: () => void;
  readonly children: ReactNode;
  readonly footer?: ReactNode;
  readonly className?: string;
}

export function GlassModal({ open, title, description, onClose, children, footer, className }: GlassModalProps) {
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
            className="absolute inset-0 cursor-default bg-ink-950/70 backdrop-blur-sm"
            variants={{ hidden: { opacity: 0 }, visible: { opacity: 1 } }}
          />
          <m.div
            role="dialog"
            aria-modal="true"
            aria-label={title}
            variants={RISE_IN}
            transition={MOTION_SPRING}
            className={cx(
              "glass-raised relative z-10 flex max-h-[90vh] w-full max-w-xl flex-col gap-5 overflow-y-auto rounded-3xl p-6",
              className,
            )}
          >
            <header className="flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-semibold text-mist-100">{title}</h2>
                {description ? <p className="mt-1 text-sm text-mist-400">{description}</p> : null}
              </div>
              <GlassButton variant="ghost" size="sm" onClick={onClose} aria-label="Đóng">
                <X size={16} aria-hidden />
              </GlassButton>
            </header>
            <div className="flex flex-col gap-4">{children}</div>
            {footer ? <footer className="flex justify-end gap-2">{footer}</footer> : null}
          </m.div>
        </m.div>
      ) : null}
    </AnimatePresence>
  );
}
