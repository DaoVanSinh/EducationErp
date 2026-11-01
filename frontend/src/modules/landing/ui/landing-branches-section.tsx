import { LANDING_SECTION_ID, type LandingBranch } from "@/modules/landing/model/landing-schema";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { m } from "framer-motion";
import { ArrowRight, Building2, MapPin } from "lucide-react";

interface LandingBranchesSectionProps {
  readonly branches: readonly LandingBranch[];
  readonly onConsultBranch: (branchId: string) => void;
}

export function LandingBranchesSection({
  branches,
  onConsultBranch,
}: LandingBranchesSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.branches} className="bg-white py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="MẠNG LƯỚI CƠ SỞ ĐÀO TẠO"
          title="Hệ thống"
          highlight="Chi nhánh & Cơ sở học tập"
          subtitle="Không gian học tập hiện đại, thuận tiện di chuyển với các cơ sở đào tạo sẵn sàng đón tiếp học viên."
        />

        {branches.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-[#fed7aa] bg-[#fffaf5] p-12 text-center">
            <Building2 className="mx-auto h-10 w-10 text-[#ea580c]" />
            <p className="mt-3 text-[15px] font-bold text-[#1e293b]">
              Đang cập nhật danh sách cơ sở đào tạo
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {branches.map((branch, idx) => (
              <m.article
                key={branch.id}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: (idx % 3) * 0.06 }}
                className="card-3d-hover flex flex-col justify-between rounded-[24px] border border-[#ffedd5] bg-[#fffaf5] p-6 transition-colors hover:bg-white"
              >
                <div>
                  <div className="mb-3 flex items-center justify-between">
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3 py-1 text-[11px] font-bold text-[#ea580c] uppercase">
                      <Building2 className="h-3.5 w-3.5" />
                      Cơ sở: {branch.code}
                    </span>
                    <span className="inline-flex items-center gap-1 text-[11.5px] font-semibold text-emerald-600">
                      <span className="h-2 w-2 rounded-full bg-emerald-500" />
                      Đang tuyển sinh
                    </span>
                  </div>

                  <h3 className="text-[19px] font-extrabold text-[#1e293b]">{branch.name}</h3>

                  <p className="mt-3 flex items-start gap-2 text-[13.5px] leading-relaxed text-[#475569]">
                    <MapPin className="mt-0.5 h-4 w-4 shrink-0 text-[#ea580c]" />
                    <span>{branch.address ?? "Đang cập nhật địa chỉ chi tiết"}</span>
                  </p>
                </div>

                <div className="mt-6 border-t border-[#ffedd5] pt-4">
                  <button
                    type="button"
                    onClick={() => onConsultBranch(branch.id)}
                    className="inline-flex w-full cursor-pointer items-center justify-center gap-2 rounded-full bg-white px-4 py-2.5 text-[13px] font-bold text-[#ea580c] ring-1 ring-[#fed7aa] transition-all hover:bg-gradient-to-r hover:from-[#ff8c42] hover:to-[#ff5e62] hover:text-white"
                  >
                    Đăng ký học tại cơ sở này
                    <ArrowRight className="h-3.5 w-3.5" />
                  </button>
                </div>
              </m.article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
