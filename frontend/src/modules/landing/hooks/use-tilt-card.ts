import { useMotionValue, useSpring, useTransform } from "framer-motion";
import { useRef, useState, type MouseEvent } from "react";

const TILT_SPRING = { damping: 28, stiffness: 100 } as const;

export function useTiltCard(maxTiltDeg = 8) {
  const cardRef = useRef<HTMLDivElement | null>(null);
  const [isHovered, setIsHovered] = useState(false);

  const mouseX = useMotionValue(0);
  const mouseY = useMotionValue(0);

  const rotateX = useSpring(
    useTransform(mouseY, [-0.5, 0.5], [maxTiltDeg, -maxTiltDeg]),
    TILT_SPRING,
  );
  const rotateY = useSpring(
    useTransform(mouseX, [-0.5, 0.5], [-maxTiltDeg, maxTiltDeg]),
    TILT_SPRING,
  );
  const scale = useSpring(isHovered ? 1.022 : 1, TILT_SPRING);

  const handleMouseMove = (event: MouseEvent<HTMLDivElement>) => {
    const el = cardRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;
    mouseX.set(x);
    mouseY.set(y);
  };

  const handleMouseEnter = () => {
    setIsHovered(true);
  };

  const handleMouseLeave = () => {
    setIsHovered(false);
    mouseX.set(0);
    mouseY.set(0);
  };

  return {
    cardRef,
    isHovered,
    rotateX,
    rotateY,
    scale,
    onMouseMove: handleMouseMove,
    onMouseEnter: handleMouseEnter,
    onMouseLeave: handleMouseLeave,
  };
}
