import { classStatusSchema, weeklyScheduleSlotSchema } from "@/entities/class";
import { gradeColumnSchema } from "@/shared/api/schemas";
import { z } from "zod";

export const landingStatsSchema = z.object({
  activeCourseCount: z.number().int(),
  activeClassCount: z.number().int(),
  activeTeacherCount: z.number().int(),
  activeBranchCount: z.number().int(),
  maxComboDiscountPercent: z.number(),
});

export const landingCourseSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  standardSessionCount: z.number().int().nullable(),
  tuitionFee: z.number().nullable(),
  openClassCount: z.number().int(),
  gradeColumns: z.array(gradeColumnSchema),
});

export const landingClassSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  courseId: z.string().uuid(),
  courseName: z.string(),
  branchId: z.string().uuid(),
  branchName: z.string().nullable(),
  teacherName: z.string().nullable(),
  maxSeats: z.number().int(),
  schedule: z.array(weeklyScheduleSlotSchema),
  /** Lớp còn tuyển hay đã học - khách cần biết để không đăng ký nhầm một lớp đã khai giảng lâu. */
  status: classStatusSchema,
  startDate: z.string(),
  seatsLeft: z.number().int(),
});

export const landingTeacherSchema = z.object({
  id: z.string().uuid(),
  fullName: z.string(),
  subjects: z.array(z.string()),
  bio: z.string().nullable(),
});

export const landingBranchSchema = z.object({
  id: z.string().uuid(),
  code: z.string(),
  name: z.string(),
  address: z.string().nullable(),
});

export const landingComboTierSchema = z.object({
  id: z.string().uuid(),
  minCourseCount: z.number().int(),
  discountPercent: z.number(),
});

export const landingCenterInfoSchema = z.object({
  brandName: z.string(),
  brandSubtitle: z.string(),
  tagline: z.string(),
  heroBadge: z.string(),
  heroTitle: z.string(),
  heroHighlight: z.string(),
  heroDescription: z.string(),
  hotline: z.string(),
  hotlineDisplay: z.string(),
  email: z.string(),
  workTime: z.string(),
  defaultAddress: z.string(),
  zaloUrl: z.string(),
  messengerUrl: z.string(),
  footerDescription: z.string(),
});

export const landingRoadmapStepSchema = z.object({
  id: z.string().uuid().optional(),
  step: z.string(),
  badgeText: z.string(),
  subTag: z.string(),
  title: z.string(),
  duration: z.string(),
  target: z.string(),
  colorTheme: z.string(),
  features: z.array(z.string()),
});

export const landingFeatureSchema = z.object({
  id: z.string().uuid().optional(),
  iconKey: z.string(),
  variant: z.string(),
  subtitle: z.string(),
  title: z.string(),
  description: z.string(),
});

export const landingFaqSchema = z.object({
  id: z.string().uuid().optional(),
  question: z.string(),
  answer: z.string(),
});

export const landingContentConfigSchema = z.object({
  centerInfo: landingCenterInfoSchema,
  roadmapSteps: z.array(landingRoadmapStepSchema),
  whyChooseFeatures: z.array(landingFeatureSchema),
  faqs: z.array(landingFaqSchema),
});

export const landingOverviewSchema = z.object({
  stats: landingStatsSchema,
  courses: z.array(landingCourseSchema),
  classes: z.array(landingClassSchema),
  teachers: z.array(landingTeacherSchema),
  branches: z.array(landingBranchSchema),
  comboDiscountTiers: z.array(landingComboTierSchema),
  config: landingContentConfigSchema,
});

export const CONSULTATION_STATUS = {
  newLead: "NEW",
  contacted: "CONTACTED",
  enrolled: "ENROLLED",
  closed: "CLOSED",
} as const;

export type ConsultationStatus = (typeof CONSULTATION_STATUS)[keyof typeof CONSULTATION_STATUS];

export const CONSULTATION_STATUS_LABEL: Record<ConsultationStatus, string> = {
  [CONSULTATION_STATUS.newLead]: "Mới đăng ký",
  [CONSULTATION_STATUS.contacted]: "Đã liên hệ",
  [CONSULTATION_STATUS.enrolled]: "Đã ghi danh",
  [CONSULTATION_STATUS.closed]: "Đã đóng",
};

export const consultationItemSchema = z.object({
  id: z.string().uuid(),
  fullName: z.string(),
  phone: z.string(),
  email: z.string().nullable(),
  targetCourseId: z.string().uuid().nullable(),
  targetCourseName: z.string().nullable(),
  targetBranchId: z.string().uuid().nullable(),
  targetBranchName: z.string().nullable(),
  notes: z.string().nullable(),
  status: z.enum([
    CONSULTATION_STATUS.newLead,
    CONSULTATION_STATUS.contacted,
    CONSULTATION_STATUS.enrolled,
    CONSULTATION_STATUS.closed,
  ]),
  createdAt: z.string(),
});

export type LandingStats = z.infer<typeof landingStatsSchema>;
export type LandingCourse = z.infer<typeof landingCourseSchema>;
export type LandingClass = z.infer<typeof landingClassSchema>;
export type LandingTeacher = z.infer<typeof landingTeacherSchema>;
export type LandingBranch = z.infer<typeof landingBranchSchema>;
export type LandingComboTier = z.infer<typeof landingComboTierSchema>;
export type LandingCenterInfo = z.infer<typeof landingCenterInfoSchema>;
export type RoadmapStep = z.infer<typeof landingRoadmapStepSchema>;
export type WhyChooseFeature = z.infer<typeof landingFeatureSchema>;
export type FaqItemData = z.infer<typeof landingFaqSchema>;
export type LandingContentConfig = z.infer<typeof landingContentConfigSchema>;
export type LandingOverview = z.infer<typeof landingOverviewSchema>;
export type ConsultationItem = z.infer<typeof consultationItemSchema>;

export const LANDING_TAB = {
  home: "home",
  courses: "courses",
  teachers: "teachers",
  branches: "branches",
  contact: "contact",
} as const;

export type LandingTabId = (typeof LANDING_TAB)[keyof typeof LANDING_TAB];

export const LANDING_SECTION_ID = {
  overview: "overview",
  courses: "courses",
  calculator: "calculator",
  roadmap: "roadmap",
  whyUs: "why-us",
  teachers: "teachers",
  branches: "branches",
  faq: "faq",
  consultation: "consultation",
} as const;

export type LandingMorphVariant = "blue" | "rose" | "emerald" | "amber" | "purple" | "cyan";

export interface LandingNavItem {
  readonly id: LandingTabId;
  readonly label: string;
}

export const LANDING_NAV_ITEMS: readonly LandingNavItem[] = [
  { id: LANDING_TAB.home, label: "TRANG CHỦ" },
  { id: LANDING_TAB.courses, label: "KHOÁ HỌC & LỚP MỞ" },
  { id: LANDING_TAB.teachers, label: "ĐỘI NGŨ GIẢNG VIÊN" },
  { id: LANDING_TAB.branches, label: "HỆ THỐNG CƠ SỞ" },
  { id: LANDING_TAB.contact, label: "LIÊN HỆ TƯ VẤN" },
] as const;

export interface StickySubNavItem {
  readonly id: string;
  readonly label: string;
}

export const STICKY_SUB_NAV_ITEMS: readonly StickySubNavItem[] = [
  { id: LANDING_SECTION_ID.overview, label: "Tổng quan" },
  { id: LANDING_SECTION_ID.courses, label: "Khoá học & Lớp mở" },
  { id: LANDING_SECTION_ID.calculator, label: "Dự toán học phí & Combo" },
  { id: LANDING_SECTION_ID.roadmap, label: "Lộ trình đào tạo" },
  { id: LANDING_SECTION_ID.whyUs, label: "Điểm khác biệt" },
  { id: LANDING_SECTION_ID.teachers, label: "Giảng viên" },
  { id: LANDING_SECTION_ID.branches, label: "Cơ sở đào tạo" },
  { id: LANDING_SECTION_ID.faq, label: "FAQ" },
] as const;

export interface ConsultationFormData {
  readonly fullName: string;
  readonly phone: string;
  readonly email: string;
  readonly targetCourseId: string;
  readonly targetBranchId: string;
  readonly notes: string;
}

export interface SubmitConsultationPayload {
  readonly fullName: string;
  readonly phone: string;
  readonly email: string | null;
  readonly targetCourseId: string | null;
  readonly targetBranchId: string | null;
  readonly notes: string | null;
}
