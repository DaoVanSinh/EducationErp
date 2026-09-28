import { LazyMotion } from "framer-motion";
import type { ReactNode } from "react";

/**
 * Tải phần animation của framer-motion theo nhu cầu, và bật strict để mọi nơi buộc phải dùng thẻ
 * `m.*`. Nếu ở đâu đó lỡ dùng `motion.*`, cả thư viện đầy đủ sẽ bị kéo vào bundle - strict biến sai
 * sót đó thành lỗi lúc chạy dev thay vì một bundle phình lên âm thầm.
 */
const loadDomAnimation = () => import("framer-motion").then((module) => module.domAnimation);

export function MotionProvider({ children }: { readonly children: ReactNode }) {
  return (
    <LazyMotion features={loadDomAnimation} strict>
      {children}
    </LazyMotion>
  );
}
