import { LANDING_SECTION_ID, type LandingTeacher } from "@/modules/landing/model/landing-schema";
import { LandingSectionHeader } from "@/modules/landing/ui/landing-section-header";
import { formatter } from "@/shared/lib/format";
import { m } from "framer-motion";
import { Award, BookOpen, GraduationCap } from "lucide-react";

interface LandingTeachersSectionProps {
  readonly teachers: readonly LandingTeacher[];
}

export function LandingTeachersSection({ teachers }: LandingTeachersSectionProps) {
  return (
    <section id={LANDING_SECTION_ID.teachers} className="bg-[#fffaf5] py-20 lg:py-24">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-8">
        <LandingSectionHeader
          badge="ĐỘI NGŨ GIẢNG VIÊN CHUYÊN MÔN CAO"
          title="Đội ngũ"
          highlight="Giảng viên & Chuyên gia học thuật"
          subtitle="Đội ngũ giảng viên giàu kinh nghiệm thực chiến, tận tâm đồng hành và sát sao cùng từng học viên trong suốt khoá học."
        />

        {teachers.length === 0 ? (
          <div className="rounded-[24px] border border-dashed border-[#fed7aa] bg-white p-12 text-center">
            <GraduationCap className="mx-auto h-10 w-10 text-[#ea580c]" />
            <p className="mt-3 text-[15px] font-bold text-[#1e293b]">
              Danh sách giảng viên đang được cập nhật
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
            {teachers.map((teacher, idx) => (
              <m.article
                key={teacher.id}
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.35, delay: (idx % 3) * 0.06 }}
                className="card-3d-hover flex flex-col justify-between rounded-[24px] border border-[#ffedd5] bg-white p-6 shadow-[0_8px_28px_rgba(255,140,66,0.06)]"
              >
                <div>
                  <div className="flex items-center gap-4">
                    <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-[#ff8c42] to-[#ff5e62] text-[18px] font-black text-white shadow-sm">
                      {formatter.initials(teacher.fullName)}
                    </div>
                    <div>
                      <span className="inline-flex items-center gap-1 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-0.5 text-[11px] font-bold text-[#ea580c]">
                        <Award className="h-3 w-3" />
                        Giảng viên chuyên môn
                      </span>
                      <h3 className="mt-1 text-[18px] font-extrabold text-[#1e293b]">
                        {teacher.fullName}
                      </h3>
                    </div>
                  </div>

                  <p className="mt-4 text-[13.5px] leading-relaxed text-[#475569]">
                    {teacher.bio ??
                      "Giảng viên giàu kinh nghiệm chuyên môn, trực tiếp phụ trách giảng dạy và hướng dẫn học viên đạt mục tiêu đầu ra."}
                  </p>
                </div>

                {teacher.subjects.length > 0 && (
                  <div className="mt-5 border-t border-[#ffedd5] pt-4">
                    <p className="mb-2 flex items-center gap-1.5 text-[11px] font-bold tracking-wider text-[#64748b] uppercase">
                      <BookOpen className="h-3.5 w-3.5 text-[#ea580c]" />
                      Chuyên môn phụ trách
                    </p>
                    <div className="flex flex-wrap gap-1.5">
                      {teacher.subjects.map((subject) => (
                        <span
                          key={subject}
                          className="rounded-lg border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-1 text-[12px] font-bold text-[#ea580c]"
                        >
                          {subject}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </m.article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}
