import type { Transition, Variants } from "framer-motion";

/**
 * Bộ chuyển động dùng chung. Gom vào một chỗ để toàn ứng dụng có cùng "cảm giác vật lý": kính trượt
 * vào chỗ rồi dừng mềm, không nảy.
 */
export const MOTION_SPRING: Transition = {
  type: "spring",
  stiffness: 260,
  damping: 26,
  mass: 0.9,
};

export const MOTION_SPRING_SOFT: Transition = {
  type: "spring",
  stiffness: 170,
  damping: 24,
};

export const FADE_IN: Variants = {
  hidden: { opacity: 0 },
  visible: { opacity: 1 },
};

export const RISE_IN: Variants = {
  hidden: { opacity: 0, y: 14, scale: 0.985 },
  visible: { opacity: 1, y: 0, scale: 1 },
  exit: { opacity: 0, y: -10, scale: 0.985 },
};

export const SLIDE_OVER: Variants = {
  hidden: { opacity: 0, x: 24 },
  visible: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: 24 },
};

/** Danh sách xuất hiện lần lượt: độ trễ theo chỉ số, chặn trên để danh sách dài không chờ lâu. */
export const staggerDelay = (index: number): Transition => ({
  ...MOTION_SPRING,
  delay: Math.min(index, 8) * 0.045,
});
