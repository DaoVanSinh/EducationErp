import { useLandingController } from "@/modules/landing/hooks/use-landing-controller";
import { LANDING_TAB } from "@/modules/landing/model/landing-schema";
import { LandingBranchesSection } from "@/modules/landing/ui/landing-branches-section";
import { LandingCalculatorSection } from "@/modules/landing/ui/landing-calculator-section";
import { LandingConsultationCta } from "@/modules/landing/ui/landing-consultation-cta";
import { LandingConsultationModal } from "@/modules/landing/ui/landing-consultation-modal";
import { LandingCourseGrid } from "@/modules/landing/ui/landing-course-grid";
import { LandingCourseModal } from "@/modules/landing/ui/landing-course-modal";
import { LandingFaqSection } from "@/modules/landing/ui/landing-faq-section";
import { LandingFloatingActions } from "@/modules/landing/ui/landing-floating-actions";
import { LandingFooter } from "@/modules/landing/ui/landing-footer";
import { LandingHeader } from "@/modules/landing/ui/landing-header";
import { LandingHeroSection } from "@/modules/landing/ui/landing-hero-section";
import { LandingOverviewCards } from "@/modules/landing/ui/landing-overview-cards";
import { LandingRoadmapSection } from "@/modules/landing/ui/landing-roadmap-section";
import { LandingStickySubnav } from "@/modules/landing/ui/landing-sticky-subnav";
import { LandingTeachersSection } from "@/modules/landing/ui/landing-teachers-section";
import { LandingWhyUsSection } from "@/modules/landing/ui/landing-why-us-section";

export function LandingPage() {
  const {
    overview,
    isLoading,
    activeTab,
    activeSection,
    isScrolled,
    isMobileMenuOpen,
    openFaqIndex,
    catalogFilter,
    calculator,
    groupDiscountPercent,
    consultationForm,
    onSelectTab,
    onScrollToSection,
    onToggleMobileMenu,
    onCloseMobileMenu,
    onConsultClick,
    onToggleFaq,
  } = useLandingController();

  const { centerInfo, roadmapSteps, whyChooseFeatures, faqs } = overview.config;
  const showHomeSections = activeTab === LANDING_TAB.home;
  const showCoursesSection = showHomeSections || activeTab === LANDING_TAB.courses;
  const showTeachersSection = showHomeSections || activeTab === LANDING_TAB.teachers;
  const showBranchesSection = showHomeSections || activeTab === LANDING_TAB.branches;

  return (
    <div className="min-h-screen bg-white text-[#1e293b] antialiased">
      <LandingHeader
        centerInfo={centerInfo}
        activeTab={activeTab}
        isScrolled={isScrolled}
        isMobileMenuOpen={isMobileMenuOpen}
        primaryBranch={overview.branches[0]}
        onSelectTab={onSelectTab}
        onToggleMobileMenu={onToggleMobileMenu}
        onCloseMobileMenu={onCloseMobileMenu}
        onConsultClick={() => onConsultClick()}
      />

      {showHomeSections && (
        <>
          <LandingHeroSection
            centerInfo={centerInfo}
            stats={overview.stats}
            featuredCourse={overview.courses[0]}
            onConsultClick={() => onConsultClick()}
            onScrollToSection={onScrollToSection}
          />
          <LandingStickySubnav
            activeSection={activeSection}
            onScrollToSection={onScrollToSection}
            onConsultClick={() => onConsultClick()}
          />
          <LandingOverviewCards stats={overview.stats} onSelectTab={onSelectTab} />
        </>
      )}

      <main>
        {showCoursesSection && (
          <>
            <LandingCourseGrid
              categories={catalogFilter.categories}
              selectedCategory={catalogFilter.selectedCategory}
              searchQuery={catalogFilter.searchQuery}
              filteredCourses={catalogFilter.filteredCourses}
              isLoading={isLoading}
              onSelectCategory={catalogFilter.onSelectCategory}
              onSearchChange={catalogFilter.onSearchChange}
              onOpenCourseDetail={catalogFilter.onOpenCourseDetail}
            />
            <LandingCalculatorSection
              calculator={calculator}
              comboTiers={overview.comboDiscountTiers}
              onConsult={() => onConsultClick(calculator.consultRequest)}
            />
          </>
        )}

        {showHomeSections && (
          <>
            <LandingRoadmapSection steps={roadmapSteps} />
            <LandingWhyUsSection features={whyChooseFeatures} />
          </>
        )}

        {showTeachersSection && <LandingTeachersSection teachers={overview.teachers} />}

        {showBranchesSection && (
          <LandingBranchesSection
            branches={overview.branches}
            onConsultBranch={(branchId) => onConsultClick({ branchId })}
          />
        )}

        {showHomeSections && (
          <LandingFaqSection
            faqItems={faqs}
            openFaqIndex={openFaqIndex}
            onToggleFaq={onToggleFaq}
          />
        )}

        <LandingConsultationCta
          centerInfo={centerInfo}
          courses={overview.courses}
          branches={overview.branches}
          formData={consultationForm.formData}
          errors={consultationForm.errors}
          isSubmitting={consultationForm.isSubmitting}
          onChange={consultationForm.onChange}
          onSubmit={consultationForm.onSubmit}
        />
      </main>

      <LandingFooter
        centerInfo={centerInfo}
        branches={overview.branches}
        onSelectTab={onSelectTab}
      />

      <LandingFloatingActions centerInfo={centerInfo} isScrolled={isScrolled} />

      <LandingCourseModal
        course={catalogFilter.selectedCourse}
        courseClasses={catalogFilter.selectedCourseClasses}
        groupDiscountPercent={groupDiscountPercent}
        onClose={catalogFilter.onCloseCourseDetail}
        onConsultCourse={(opts) => onConsultClick(opts)}
      />

      <LandingConsultationModal
        isOpen={consultationForm.isSuccessModalOpen}
        brandName={centerInfo.brandName}
        hotlineDisplay={centerInfo.hotlineDisplay}
        onClose={consultationForm.onCloseSuccessModal}
      />
    </div>
  );
}
