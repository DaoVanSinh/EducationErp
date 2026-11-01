import { DAY_OF_WEEK_LABEL } from "@/entities/class";
import type { LandingClass, LandingCourse } from "@/modules/landing/model/landing-schema";
import { formatter } from "@/shared/lib/format";
import { AnimatePresence, m } from "framer-motion";
import { Award, BookOpen, Building2, Calendar, Clock, Sparkles, UserCheck, Users, X } from "lucide-react";

interface LandingCourseModalProps {
  readonly course: LandingCourse | null;
  readonly courseClasses: readonly LandingClass[];
  readonly onClose: () => void;
  readonly onConsultCourse: (options: { courseId: string; branchId?: string }) => void;
}

export function LandingCourseModal({
  course,
  courseClasses,
  onClose,
  onConsultCourse,
}: LandingCourseModalProps) {
  return (
    <AnimatePresence>
      {course !== null && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-[#431407]/25 p-4 backdrop-blur-sm"
          onClick={onClose}
          role="dialog"
          aria-modal="true"
          aria-labelledby="landing-course-modal-title"
        >
          <m.div
            initial={{ opacity: 0, scale: 0.95, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 16 }}
            transition={{ duration: 0.25 }}
            onClick={(e) => e.stopPropagation()}
            className="max-h-[88vh] w-full max-w-[760px] overflow-y-auto rounded-[28px] border border-[#fed7aa] bg-white p-6 shadow-[0_24px_64px_rgba(255,140,66,0.2)] sm:p-8"
          >
            <div className="flex items-start justify-between gap-4 border-b border-[#ffedd5] pb-5">
              <div>
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  <span className="inline-flex items-center gap-1.5 rounded-full border border-[#fed7aa] bg-[#fff7ed] px-3 py-1 text-[11px] font-bold text-[#ea580c] uppercase">
                    <BookOpen className="h-3.5 w-3.5" />
                    Mã khoá: {course.code}
                  </span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-[#fff0f3] px-3 py-1 text-[11px] font-bold text-[#f04452]">
                    <Calendar className="h-3.5 w-3.5" />
                    {course.standardSessionCount ?? 24} buổi tiêu chuẩn
                  </span>
                </div>
                <h3 id="landing-course-modal-title" className="text-[22px] font-black text-[#1e293b] sm:text-[26px]">
                  {course.name}
                </h3>
              </div>
              <button
                type="button"
                onClick={onClose}
                aria-label="Đóng chi tiết khoá học"
                className="flex h-10 w-10 shrink-0 cursor-pointer items-center justify-center rounded-full bg-[#fff7ed] text-[#475569] hover:bg-[#ffedd5]"
              >
                <X className="h-5 w-5" />
              </button>
            </div>

            <div className="mt-5 space-y-6">
              <div className="rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-4">
                <p className="text-[14px] leading-relaxed text-[#475569]">
                  {course.description ?? "Khoá học được thiết kế bài bản, theo sát tiến độ học tập và cam kết chuẩn đầu ra."}
                </p>
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 border-t border-[#ffedd5] pt-3">
                  <span className="text-[12.5px] font-semibold text-[#64748b]">Học phí trọn khoá:</span>
                  <span className="text-[20px] font-black text-[#ea580c]">
                    {course.tuitionFee !== null ? formatter.currency(course.tuitionFee) : "Liên hệ tư vấn"}
                  </span>
                </div>
              </div>

              {course.gradeColumns.length > 0 && (
                <div>
                  <h4 className="mb-3 flex items-center gap-2 text-[14px] font-extrabold text-[#1e293b] uppercase">
                    <Award className="h-4 w-4 text-[#ea580c]" />
                    Cấu trúc cột điểm đánh giá ({course.gradeColumns.length} cột)
                  </h4>
                  <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-3">
                    {course.gradeColumns.map((col) => (
                      <div key={col.name} className="rounded-2xl border border-[#ffedd5] bg-white p-3.5">
                        <p className="text-[13px] font-bold text-[#1e293b]">{col.name}</p>
                        <div className="mt-1 flex items-center justify-between text-[12px] text-[#475569]">
                          <span>Trọng số: {col.weightPercent}%</span>
                          <span className="font-semibold text-[#ea580c]">Thang {col.maxScore}đ</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              <div>
                <h4 className="mb-3 flex items-center gap-2 text-[14px] font-extrabold text-[#1e293b] uppercase">
                  <Clock className="h-4 w-4 text-[#ff5e62]" />
                  Danh sách lớp đang mở ({courseClasses.length} lớp)
                </h4>
                {courseClasses.length === 0 ? (
                  <p className="rounded-2xl border border-dashed border-[#fed7aa] bg-[#fffaf5] p-4 text-[13.5px] text-[#475569]">
                    Khoá học đang xếp lịch khai giảng đợt mới. Đăng ký tư vấn để giữ chỗ ưu tiên!
                  </p>
                ) : (
                  <div className="space-y-3">
                    {courseClasses.map((cls) => (
                      <div key={cls.id} className="flex flex-col justify-between gap-3 rounded-2xl border border-[#ffedd5] bg-[#fffaf5] p-4 sm:flex-row sm:items-center">
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="rounded-lg border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-0.5 text-[11.5px] font-bold text-[#ea580c]">
                              Lớp {cls.code}
                            </span>
                            {cls.branchName && (
                              <span className="inline-flex items-center gap-1 text-[12.5px] font-semibold text-[#ea580c]">
                                <Building2 className="h-3.5 w-3.5" />
                                {cls.branchName}
                              </span>
                            )}
                          </div>
                          <div className="mt-2 flex flex-wrap gap-3 text-[12.5px] text-[#475569]">
                            {cls.teacherName && (
                              <span className="inline-flex items-center gap-1">
                                <UserCheck className="h-3.5 w-3.5 text-emerald-600" />
                                GV: {cls.teacherName}
                              </span>
                            )}
                            <span className="inline-flex items-center gap-1">
                              <Users className="h-3.5 w-3.5 text-[#64748b]" />
                              Sĩ số tối đa: {cls.maxSeats} HV
                            </span>
                          </div>
                          {cls.schedule.length > 0 && (
                            <div className="mt-2 flex flex-wrap gap-1.5">
                              {cls.schedule.map((slot) => (
                                <span
                                  key={`${slot.dayOfWeek}-${slot.startTime}`}
                                  className="rounded-lg border border-[#fed7aa] bg-white px-2.5 py-1 text-[11.5px] font-semibold text-[#ea580c]"
                                >
                                  {DAY_OF_WEEK_LABEL[slot.dayOfWeek]}: {slot.startTime} – {slot.endTime}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                        <button
                          type="button"
                          onClick={() => onConsultCourse({ courseId: course.id, branchId: cls.branchId })}
                          className="shrink-0 cursor-pointer rounded-full bg-white px-4 py-2 text-[12.5px] font-bold text-[#ea580c] ring-1 ring-[#fed7aa] transition-all hover:bg-gradient-to-r hover:from-[#ff8c42] hover:to-[#ff5e62] hover:text-white"
                        >
                          Giữ chỗ lớp này
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <div className="mt-7 flex flex-col justify-end gap-3 border-t border-[#ffedd5] pt-5 sm:flex-row">
              <button type="button" onClick={onClose} className="cursor-pointer rounded-full border border-[#fed7aa] px-6 py-3 text-[13.5px] font-bold text-[#475569] hover:bg-[#fff7ed]">
                Đóng
              </button>
              <button
                type="button"
                onClick={() => onConsultCourse({ courseId: course.id })}
                className="inline-flex cursor-pointer items-center justify-center gap-2 rounded-full bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] px-7 py-3 text-[13.5px] font-bold text-white shadow-[0_8px_20px_rgba(255,140,66,0.28)]"
              >
                <Sparkles className="h-4 w-4" />
                Đăng ký tư vấn khoá {course.code}
              </button>
            </div>
          </m.div>
        </div>
      )}
    </AnimatePresence>
  );
}
