import {
  LANDING_NAV_ITEMS,
  type LandingBranch,
  type LandingCenterInfo,
  type LandingTabId,
} from "@/modules/landing/model/landing-schema";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { BrandLogoIcon } from "@/shared/ui/brand-logo";
import { Building2, Clock, LayoutDashboard, Mail, MapPin, Phone } from "lucide-react";
import { Link } from "react-router-dom";

interface LandingFooterProps {
  readonly centerInfo: LandingCenterInfo;
  readonly branches: readonly LandingBranch[];
  readonly onSelectTab: (tab: LandingTabId) => void;
}

export function LandingFooter({ centerInfo, branches, onSelectTab }: LandingFooterProps) {
  return (
    <footer className="border-t border-[#ffedd5] bg-white pt-16 pb-10 text-[#475569]">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 gap-10 md:grid-cols-2 lg:grid-cols-12">
          <div className="lg:col-span-4">
            <div className="flex items-center gap-3">
              <BrandLogoIcon size="md" />
              <div>
                <div className="bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] bg-clip-text text-[22px] font-black tracking-tight text-transparent leading-tight">
                  {centerInfo.brandName}
                </div>
                <p className="text-[10.5px] font-bold tracking-wider text-[#ea580c] uppercase">
                  {centerInfo.brandSubtitle}
                </p>
              </div>
            </div>
            <p className="mt-4 text-[13.5px] leading-relaxed text-[#475569]">
              {centerInfo.footerDescription}
            </p>

            <div className="mt-5 space-y-2.5 text-[13px]">
              {centerInfo.hotlineDisplay && (
                <div className="flex items-center gap-2.5">
                  <Phone className="h-4 w-4 text-[#ea580c]" />
                  <span className="font-bold text-[#1e293b]">{centerInfo.hotlineDisplay}</span>
                </div>
              )}
              {centerInfo.email && (
                <div className="flex items-center gap-2.5">
                  <Mail className="h-4 w-4 text-[#ff5e62]" />
                  <span>{centerInfo.email}</span>
                </div>
              )}
              {centerInfo.workTime && (
                <div className="flex items-center gap-2.5">
                  <Clock className="h-4 w-4 text-emerald-600" />
                  <span>{centerInfo.workTime}</span>
                </div>
              )}
            </div>
          </div>

          <div className="lg:col-span-3">
            <h4 className="text-[13px] font-extrabold tracking-wider text-[#1e293b] uppercase">
              Danh mục thông tin
            </h4>
            <ul className="mt-4 space-y-2.5 text-[13.5px]">
              {LANDING_NAV_ITEMS.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => onSelectTab(item.id)}
                    className="cursor-pointer font-medium text-[#475569] transition-colors hover:text-[#ea580c]"
                  >
                    {item.label}
                  </button>
                </li>
              ))}
              <li>
                <Link
                  to={APP_ROUTE.login}
                  className="inline-flex items-center gap-1.5 font-bold text-[#ea580c] hover:underline"
                >
                  <LayoutDashboard className="h-3.5 w-3.5" />
                  Đăng nhập Cổng Học Viên
                </Link>
              </li>
            </ul>
          </div>

          <div className="lg:col-span-5">
            <h4 className="text-[13px] font-extrabold tracking-wider text-[#1e293b] uppercase">
              Hệ thống cơ sở đào tạo ({branches.length} cơ sở)
            </h4>
            <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
              {branches.map((branch) => (
                <div
                  key={branch.id}
                  className="rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-3.5"
                >
                  <div className="flex items-center gap-1.5 text-[12.5px] font-extrabold text-[#1e293b]">
                    <Building2 className="h-3.5 w-3.5 text-[#ea580c]" />
                    <span>{branch.name}</span>
                  </div>
                  <p className="mt-1 flex items-start gap-1.5 text-[12px] text-[#475569]">
                    <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0 text-[#ff5e62]" />
                    <span>{branch.address ?? centerInfo.defaultAddress}</span>
                  </p>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-[#ffedd5] pt-6 text-[12.5px] text-[#64748b] sm:flex-row">
          <p>
            © {new Date().getFullYear()} {centerInfo.brandName}. Bảo lưu mọi quyền.
          </p>
          <p className="hero-script text-[13px] text-[#ea580c]">{centerInfo.tagline}</p>
        </div>
      </div>
    </footer>
  );
}
