import { useEffect, useRef } from "react";

interface SakuraPetal {
  x: number;
  y: number;
  z: number;
  size: number;
  vy: number;
  swayFreq: number;
  swayAmp: number;
  rx: number;
  ry: number;
  rz: number;
  drx: number;
  dry: number;
  drz: number;
  opacity: number;
}

const PETAL_COUNT = 68;

function createPetal(width: number, height: number, randomY: boolean): SakuraPetal {
  return {
    x: Math.random() * width,
    y: randomY ? Math.random() * height : -24,
    z: Math.random() * 0.65 + 0.35,
    size: Math.random() * 7 + 7,
    vy: Math.random() * 0.55 + 0.32,
    swayFreq: Math.random() * 0.8 + 0.45,
    swayAmp: Math.random() * 0.65 + 0.25,
    rx: Math.random() * Math.PI * 2,
    ry: Math.random() * Math.PI * 2,
    rz: Math.random() * Math.PI * 2,
    drx: (Math.random() - 0.5) * 0.018 + 0.006,
    dry: (Math.random() - 0.5) * 0.02,
    drz: (Math.random() - 0.5) * 0.014,
    opacity: Math.random() * 0.32 + 0.42,
  };
}

function drawPetalShape(ctx: CanvasRenderingContext2D, p: SakuraPetal) {
  ctx.save();
  ctx.translate(p.x, p.y);
  ctx.rotate(p.rz);
  const scaleX = Math.max(0.28, Math.abs(Math.cos(p.ry))) * p.z;
  const scaleY = Math.max(0.35, Math.abs(Math.sin(p.rx))) * p.z;
  ctx.scale(scaleX, scaleY);

  const r = p.size;
  const grad = ctx.createLinearGradient(0, -r, 0, r);
  grad.addColorStop(0, `rgba(255, 245, 247, ${p.opacity})`);
  grad.addColorStop(0.45, `rgba(254, 205, 211, ${p.opacity})`);
  grad.addColorStop(1, `rgba(253, 164, 175, ${p.opacity})`);

  ctx.fillStyle = grad;
  ctx.beginPath();
  ctx.moveTo(0, r);
  ctx.bezierCurveTo(-r * 0.75, r * 0.45, -r * 0.95, -r * 0.35, -r * 0.35, -r * 0.92);
  ctx.bezierCurveTo(-r * 0.12, -r * 0.68, r * 0.12, -r * 0.68, r * 0.35, -r * 0.92);
  ctx.bezierCurveTo(r * 0.95, -r * 0.35, r * 0.75, r * 0.45, 0, r);
  ctx.closePath();
  ctx.fill();
  ctx.restore();
}

/**
 * Hiệu ứng cánh hoa 3D rơi nhẹ nhàng với chuyển động lắc lư & parallax theo chuột.
 */
export function useSakuraCanvas() {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let width = canvas.parentElement?.clientWidth ?? window.innerWidth;
    let height = canvas.parentElement?.clientHeight ?? 680;
    const dpr = Math.min(window.devicePixelRatio ?? 1, 2);

    const syncSize = () => {
      width = canvas.parentElement?.clientWidth ?? window.innerWidth;
      height = canvas.parentElement?.clientHeight ?? 680;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    syncSize();

    const petals = Array.from({ length: PETAL_COUNT }, () => createPetal(width, height, true));
    let mouseOffsetX = 0;
    let targetOffsetX = 0;

    const handleMouseMove = (e: MouseEvent) => {
      targetOffsetX = ((e.clientX / window.innerWidth) - 0.5) * 0.45;
    };

    window.addEventListener("resize", syncSize);
    window.addEventListener("mousemove", handleMouseMove);

    let frameId = 0;
    const startTime = performance.now();

    const renderFrame = () => {
      frameId = window.requestAnimationFrame(renderFrame);
      const elapsed = (performance.now() - startTime) * 0.001;
      mouseOffsetX += (targetOffsetX - mouseOffsetX) * 0.04;

      ctx.clearRect(0, 0, width, height);

      for (let i = 0; i < petals.length; i += 1) {
        const p = petals[i];
        if (!p) continue;
        p.y += p.vy * p.z;
        p.x += Math.sin(elapsed * p.swayFreq + i) * p.swayAmp + mouseOffsetX * p.z;
        p.rx += p.drx;
        p.ry += p.dry;
        p.rz += p.drz;

        if (p.y > height + 24) {
          p.y = -20;
          p.x = Math.random() * width;
        }
        if (p.x < -24) p.x = width + 20;
        if (p.x > width + 24) p.x = -20;

        drawPetalShape(ctx, p);
      }
    };

    renderFrame();

    return () => {
      window.cancelAnimationFrame(frameId);
      window.removeEventListener("resize", syncSize);
      window.removeEventListener("mousemove", handleMouseMove);
    };
  }, []);

  return { canvasRef };
}
