import type { LandingCenterInfo } from "@/modules/landing/model/landing-schema";
import { ArrowUp, MessageCircle, PhoneCall } from "lucide-react";

interface LandingFloatingActionsProps {
  readonly centerInfo: LandingCenterInfo;
  readonly isScrolled: boolean;
}

export function LandingFloatingActions({ centerInfo, isScrolled }: LandingFloatingActionsProps) {
  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="fixed right-5 bottom-6 z-40 flex flex-col items-end gap-3">
      {centerInfo.zaloUrl && (
        <a
          href={centerInfo.zaloUrl}
          target="_blank"
          rel="noreferrer"
          aria-label="Chat Zalo tư vấn"
          className="flex h-12 w-12 items-center justify-center rounded-full border border-[#fed7aa] bg-[#fff7ed] text-[12px] font-black text-[#ea580c] shadow-[0_8px_20px_rgba(255,140,66,0.22)] transition-transform hover:scale-105"
        >
          Zalo
        </a>
      )}

      {centerInfo.messengerUrl && (
        <a
          href={centerInfo.messengerUrl}
          target="_blank"
          rel="noreferrer"
          aria-label="Chat Messenger tư vấn"
          className="flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-tr from-[#ff8c42] to-[#fb923c] text-white shadow-[0_8px_20px_rgba(255,140,66,0.32)] transition-transform hover:scale-105"
        >
          <MessageCircle className="h-5 w-5" />
        </a>
      )}

      {centerInfo.hotline && (
        <a
          href={`tel:${centerInfo.hotline}`}
          aria-label={`Gọi Hotline ${centerInfo.hotlineDisplay}`}
          className="relative flex h-12 w-12 items-center justify-center rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-[0_8px_24px_rgba(255,94,98,0.38)] transition-transform hover:scale-105"
        >
          <span
            aria-hidden
            className="absolute inset-0 rounded-full border-2 border-[#ff8c42]"
            style={{ animation: "pingRing 2s cubic-bezier(0, 0, 0.2, 1) infinite" }}
          />
          <PhoneCall className="h-5 w-5" />
        </a>
      )}

      {isScrolled && (
        <button
          type="button"
          onClick={scrollToTop}
          aria-label="Cuộn lên đầu trang"
          className="flex h-11 w-11 cursor-pointer items-center justify-center rounded-full border border-[#fed7aa] bg-white text-[#ea580c] shadow-md transition-colors hover:bg-[#fff7ed]"
        >
          <ArrowUp className="h-4 w-4" />
        </button>
      )}
    </div>
  );
}
