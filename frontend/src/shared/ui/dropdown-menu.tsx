import { useClickOutside } from "@/shared/lib/use-click-outside";
import { cx } from "@/shared/lib/class-names";
import { MOTION_SPRING } from "@/shared/lib/motion";
import { AnimatePresence, m } from "framer-motion";
import { type ReactNode, useEffect, useRef } from "react";

export interface DropdownMenuProps {
  readonly open: boolean;
  readonly onClose: () => void;
  readonly className?: string;
  readonly children: ReactNode;
}

/**
 * Panel neo dưới trigger của caller — caller bọc trigger+menu trong một `relative`, panel này tự lo
 * định vị `absolute`. Tự đóng khi click ra ngoài hoặc nhấn Escape, không cần caller tự lắp hai thứ đó.
 */
export function DropdownMenu({ open, onClose, className, children }: DropdownMenuProps) {
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, onClose, open);

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
          ref={ref}
          initial={{ opacity: 0, y: -6, scale: 0.98 }}
          animate={{ opacity: 1, y: 0, scale: 1 }}
          exit={{ opacity: 0, y: -6, scale: 0.98 }}
          transition={MOTION_SPRING}
          className={cx(
            "glass-raised absolute z-50 min-w-56 rounded-2xl border border-white/90 bg-white/95 p-1.5 shadow-2xl backdrop-blur-2xl",
            className,
          )}
        >
          {children}
        </m.div>
      ) : null}
    </AnimatePresence>
  );
}
