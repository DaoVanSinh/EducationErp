import { useLandingOverview } from "@/modules/landing/api/use-landing-overview";
import { useConsultationForm, type ConsultPrefill } from "@/modules/landing/hooks/use-consultation-form";
import { useLandingCatalogFilter } from "@/modules/landing/hooks/use-landing-catalog-filter";
import { useTuitionCalculator } from "@/modules/landing/hooks/use-tuition-calculator";
import { appliedDiscountPercent, GROUP_SIZE_LIMITS } from "@/modules/landing/model/tuition-calculator-rules";
import {
  LANDING_SECTION_ID,
  LANDING_TAB,
  type LandingOverview,
  type LandingTabId,
} from "@/modules/landing/model/landing-schema";
import { useEffect, useState } from "react";

const EMPTY_OVERVIEW: LandingOverview = {
  stats: {
    activeCourseCount: 0,
    activeClassCount: 0,
    activeTeacherCount: 0,
    activeBranchCount: 0,
    maxComboDiscountPercent: 0,
  },
  courses: [],
  classes: [],
  teachers: [],
  branches: [],
  comboDiscountTiers: [],
  config: {
    centerInfo: {
      brandName: "DS Academy",
      brandSubtitle: "Học Viện Đào Tạo & Phát Triển Năng Lực Toàn Diện",
      tagline: "",
      heroBadge: "",
      heroTitle: "",
      heroHighlight: "",
      heroDescription: "",
      hotline: "",
      hotlineDisplay: "",
      email: "",
      workTime: "",
      defaultAddress: "",
      zaloUrl: "",
      messengerUrl: "",
      footerDescription: "",
    },
    roadmapSteps: [],
    whyChooseFeatures: [],
    faqs: [],
  },
};

function normalizeBrandOverview(raw: LandingOverview): LandingOverview {
  const currentBrand = raw.config.centerInfo.brandName.trim();
  if (currentBrand === "" || currentBrand === "Học Viện Đào Tạo Quốc Tế" || currentBrand === "EduERP") {
    return {
      ...raw,
      config: {
        ...raw.config,
        centerInfo: {
          ...raw.config.centerInfo,
          brandName: "DS Academy",
        },
      },
    };
  }
  return raw;
}

export function useLandingController() {
  const overviewQuery = useLandingOverview();
  const overview = overviewQuery.data ? normalizeBrandOverview(overviewQuery.data) : EMPTY_OVERVIEW;

  const [activeTab, setActiveTab] = useState<LandingTabId>(LANDING_TAB.home);
  const [activeSection, setActiveSection] = useState<string>(LANDING_SECTION_ID.overview);
  const [isScrolled, setIsScrolled] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [openFaqIndex, setOpenFaqIndex] = useState<number | null>(0);

  useEffect(() => {
    const onScroll = () => setIsScrolled(window.scrollY > 24);
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const catalogFilter = useLandingCatalogFilter(
    overview.courses,
    overview.classes,
    overview.branches,
  );
  const calculator = useTuitionCalculator(overview.courses, overview.comboDiscountTiers);
  const consultationForm = useConsultationForm();

  const handleSelectTab = (tab: LandingTabId) => {
    setActiveTab(tab);
    setIsMobileMenuOpen(false);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const handleScrollToSection = (sectionId: string) => {
    setActiveSection(sectionId);
    if (activeTab !== LANDING_TAB.home) {
      setActiveTab(LANDING_TAB.home);
      window.setTimeout(() => {
        const el = document.getElementById(sectionId);
        el?.scrollIntoView({ behavior: "smooth" });
      }, 80);
      return;
    }
    const el = document.getElementById(sectionId);
    el?.scrollIntoView({ behavior: "smooth" });
  };

  const handleConsultClick = (options?: ConsultPrefill) => {
    if (options) {
      consultationForm.onPrefill(options);
    }
    catalogFilter.onCloseCourseDetail();
    handleScrollToSection(LANDING_SECTION_ID.consultation);
  };

  const handleToggleFaq = (index: number) => {
    setOpenFaqIndex((prev) => (prev === index ? null : index));
  };

  return {
    overview,
    isLoading: overviewQuery.isLoading,
    isError: overviewQuery.isError,
    activeTab,
    activeSection,
    isScrolled,
    isMobileMenuOpen,
    openFaqIndex,
    catalogFilter,
    calculator,
    groupDiscountPercent: appliedDiscountPercent(GROUP_SIZE_LIMITS.min, overview.comboDiscountTiers),
    consultationForm,
    onSelectTab: handleSelectTab,
    onScrollToSection: handleScrollToSection,
    onToggleMobileMenu: () => setIsMobileMenuOpen((prev) => !prev),
    onCloseMobileMenu: () => setIsMobileMenuOpen(false),
    onConsultClick: handleConsultClick,
    onToggleFaq: handleToggleFaq,
  };
}
