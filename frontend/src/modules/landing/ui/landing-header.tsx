import {
  LANDING_NAV_ITEMS,
  LANDING_TAB,
  type LandingBranch,
  type LandingCenterInfo,
  type LandingTabId,
} from "@/modules/landing/model/landing-schema";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { cx } from "@/shared/lib/class-names";
import { BrandLogoIcon } from "@/shared/ui/brand-logo";
import {
  ChevronDown,
  Headphones,
  Home,
  LayoutDashboard,
  MapPin,
  Menu,
  Phone,
  X,
} from "lucide-react";
import { Link } from "react-router-dom";

export interface LandingHeaderProps {
  readonly centerInfo: LandingCenterInfo;
  readonly activeTab: LandingTabId;
  readonly isScrolled: boolean;
  readonly isMobileMenuOpen: boolean;
  readonly primaryBranch?: LandingBranch;
  readonly onSelectTab: (tab: LandingTabId) => void;
  readonly onToggleMobileMenu: () => void;
  readonly onCloseMobileMenu: () => void;
  readonly onConsultClick: () => void;
}

export function LandingHeader({
  centerInfo,
  activeTab,
  isScrolled,
  isMobileMenuOpen,
  primaryBranch,
  onSelectTab,
  onToggleMobileMenu,
  onCloseMobileMenu,
  onConsultClick,
}: LandingHeaderProps) {
  const displayAddress = primaryBranch?.address ?? centerInfo.defaultAddress;

  return (
    <>
      <div className="relative z-50 border-b border-[#ffedd5] bg-[#fff7ed] py-2 text-xs font-semibold text-[#ea580c]">
        <div className="mx-auto flex max-w-[1280px] items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-5">
            {centerInfo.hotline && (
              <a
                href={`tel:${centerInfo.hotline}`}
                className="inline-flex items-center gap-1.5 font-bold text-[#ea580c] hover:underline"
              >
                <Phone size={13} aria-hidden />
                <span>Hotline: {centerInfo.hotlineDisplay}</span>
              </a>
            )}
            {displayAddress && (
              <div className="hidden items-center gap-1.5 font-medium text-[#475569] md:inline-flex">
                <MapPin size={13} className="text-[#ff5e62]" aria-hidden />
                <span className="max-w-md truncate">{displayAddress}</span>
              </div>
            )}
          </div>
          <div className="flex items-center gap-3">
            {centerInfo.tagline && (
              <span className="hidden rounded-full border border-[#fed7aa] bg-white px-2.5 py-0.5 text-[11px] font-bold text-[#ea580c] sm:inline-block">
                ✨ {centerInfo.tagline}
              </span>
            )}
            <Link
              to={APP_ROUTE.login}
              className="inline-flex items-center gap-1 font-bold text-[#ea580c] hover:text-[#c2410c]"
            >
              <LayoutDashboard size={13} aria-hidden />
              <span>Cổng Học Viên & Giảng Viên</span>
            </Link>
          </div>
        </div>
      </div>

      <header
        className={cx(
          "sticky top-0 z-40 border-b border-[#ffedd5] bg-white/95 backdrop-blur-md transition-shadow duration-200",
          isScrolled ? "shadow-[0_4px_20px_rgba(255,140,66,0.09)]" : "shadow-2xs",
        )}
      >
        <div className="mx-auto flex h-[74px] max-w-[1280px] items-center justify-between gap-4 px-4 sm:px-6">
          <button
            type="button"
            onClick={() => onSelectTab(LANDING_TAB.home)}
            className="flex shrink-0 cursor-pointer items-center gap-3 text-left"
          >
            <BrandLogoIcon size="md" />
            <div className="flex flex-col">
              <span className="bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] bg-clip-text text-[22px] font-black tracking-tight text-transparent leading-tight">
                {centerInfo.brandName}
              </span>
              <span className="-mt-0.5 text-[9px] font-bold tracking-[0.8px] text-[#64748b] uppercase">
                {centerInfo.brandSubtitle}
              </span>
            </div>
          </button>

          <nav className="hidden items-center gap-1.5 lg:flex">
            {LANDING_NAV_ITEMS.map((item) => {
              const isActive = activeTab === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => onSelectTab(item.id)}
                  className={cx(
                    "inline-flex cursor-pointer items-center gap-1 rounded-full px-3.5 py-2 text-[12.5px] font-bold transition-all",
                    isActive
                      ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-[0_2px_8px_rgba(255,140,66,0.28)]"
                      : "text-[#334155] hover:bg-[#fff7ed] hover:text-[#ea580c]",
                  )}
                >
                  <span>{item.label}</span>
                  <ChevronDown size={13} aria-hidden />
                </button>
              );
            })}
          </nav>

          <div className="flex items-center gap-2.5">
            <button
              type="button"
              onClick={onConsultClick}
              className="inline-flex cursor-pointer items-center gap-1.5 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-4 py-2 text-[13px] font-bold text-white shadow-[0_2px_10px_rgba(255,140,66,0.3)] transition-all hover:opacity-95"
            >
              <Headphones size={15} aria-hidden />
              <span>TƯ VẤN NGAY</span>
            </button>
            <Link
              to={APP_ROUTE.login}
              className="hidden items-center gap-1.5 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-4 py-2 text-[13px] font-bold text-[#ea580c] transition-colors hover:bg-[#ffedd5] sm:inline-flex"
            >
              <LayoutDashboard size={15} aria-hidden />
              <span>Đăng nhập</span>
            </Link>
            <button
              type="button"
              onClick={onToggleMobileMenu}
              aria-label="Mở menu điều hướng"
              className="flex size-10 cursor-pointer items-center justify-center rounded-xl border border-[#fed7aa] bg-[#fff7ed] text-[#1e293b] lg:hidden"
            >
              {isMobileMenuOpen ? <X size={20} /> : <Menu size={20} />}
            </button>
          </div>
        </div>

        {isMobileMenuOpen && (
          <div className="border-t border-[#ffedd5] bg-white px-4 py-4 shadow-lg lg:hidden">
            <div className="flex flex-col gap-1.5">
              {LANDING_NAV_ITEMS.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => {
                    onSelectTab(item.id);
                    onCloseMobileMenu();
                  }}
                  className="flex w-full items-center justify-between rounded-xl px-4 py-2.5 text-left text-sm font-bold text-[#334155] hover:bg-[#fff7ed] hover:text-[#ea580c]"
                >
                  <span>{item.label}</span>
                  <ChevronDown size={14} aria-hidden />
                </button>
              ))}
            </div>
          </div>
        )}
      </header>

      <div className="border-b border-[#ffedd5]/60 bg-white py-2.5 text-[12.5px] text-[#64748b]">
        <div className="mx-auto flex max-w-[1280px] items-center gap-2 px-4 sm:px-6">
          <Home size={13} className="text-[#94a3b8]" aria-hidden />
          <span className="font-medium text-[#334155]">{centerInfo.brandName}</span>
          <span>»</span>
          <span className="font-medium text-[#94a3b8]">{centerInfo.brandSubtitle}</span>
        </div>
      </div>
    </>
  );
}
