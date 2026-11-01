import {
  ADMIN_CONFIG_TAB,
  useCenterConfigController,
  type AdminConfigTabId,
} from "@/modules/landing/hooks/use-center-config-controller";
import { AdminCenterInfoForm } from "@/modules/landing/ui/admin-center-info-form";
import { AdminConsultationsTable } from "@/modules/landing/ui/admin-consultations-table";
import { AdminLandingContentEditor } from "@/modules/landing/ui/admin-landing-content-editor";
import { APP_ROUTE } from "@/shared/constants/app-routes";
import { ExternalLink, FileText, MessageSquare, Settings } from "lucide-react";
import { Link } from "react-router-dom";

const ADMIN_TABS: readonly {
  readonly id: AdminConfigTabId;
  readonly label: string;
}[] = [
  { id: ADMIN_CONFIG_TAB.info, label: "Thông tin Trung tâm & Liên hệ" },
  { id: ADMIN_CONFIG_TAB.content, label: "Nội dung Lộ trình, Khác biệt & FAQ" },
  { id: ADMIN_CONFIG_TAB.consultations, label: "Danh sách Đăng ký Tư vấn" },
];

export function CenterConfigPage() {
  const {
    activeTab,
    currentConfig,
    consultations,
    isLoadingConfig,
    isLoadingConsultations,
    isSaving,
    saveMessage,
    onSelectTab,
    onCenterInfoChange,
    onRoadmapChange,
    onRoadmapFeaturesTextChange,
    onFeatureChange,
    onFaqChange,
    onAddFaq,
    onRemoveFaq,
    onSaveConfig,
    onStatusChange,
  } = useCenterConfigController();

  return (
    <div className="space-y-6">
      <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-slate-900">
            Cấu hình Thông tin Trung tâm & Trang giới thiệu
          </h1>
          <p className="mt-1 text-sm text-slate-600">
            Quản lý toàn bộ thông tin thương hiệu, liên hệ, nội dung lộ trình đào tạo và danh sách
            đăng ký tư vấn từ trang Landing Page.
          </p>
        </div>

        <Link
          to={APP_ROUTE.landing}
          target="_blank"
          rel="noreferrer"
          className="inline-flex items-center gap-2 rounded-xl border border-orange-200 bg-orange-50 px-4 py-2.5 text-sm font-semibold text-orange-700 transition-colors hover:bg-orange-100"
        >
          <span>Mở Trang chủ giới thiệu (/)</span>
          <ExternalLink className="h-4 w-4" />
        </Link>
      </div>

      <div className="flex flex-wrap gap-2 border-b border-slate-200 pb-3">
        {ADMIN_TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => onSelectTab(tab.id)}
              className={`inline-flex cursor-pointer items-center gap-2 rounded-xl px-4 py-2 text-sm font-semibold transition-colors ${
                isActive
                  ? "bg-gradient-to-r from-[#ff8c42] to-[#ff5e62] text-white shadow-xs"
                  : "bg-slate-100 text-slate-700 hover:bg-slate-200"
              }`}
            >
              {tab.id === ADMIN_CONFIG_TAB.info && <Settings className="h-4 w-4" />}
              {tab.id === ADMIN_CONFIG_TAB.content && <FileText className="h-4 w-4" />}
              {tab.id === ADMIN_CONFIG_TAB.consultations && <MessageSquare className="h-4 w-4" />}
              <span>{tab.label}</span>
              {tab.id === ADMIN_CONFIG_TAB.consultations && (
                <span className="rounded-full bg-white/20 px-2 py-0.5 text-xs">
                  {consultations.length}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {isLoadingConfig && (
        <div className="rounded-2xl border border-slate-200 bg-white p-8 text-center text-sm text-slate-500">
          Đang tải dữ liệu cấu hình từ hệ thống ERP...
        </div>
      )}

      {!isLoadingConfig && currentConfig !== null && activeTab === ADMIN_CONFIG_TAB.info && (
        <AdminCenterInfoForm
          centerInfo={currentConfig.centerInfo}
          isSaving={isSaving}
          saveMessage={saveMessage}
          onChange={onCenterInfoChange}
          onSubmit={onSaveConfig}
        />
      )}

      {!isLoadingConfig && currentConfig !== null && activeTab === ADMIN_CONFIG_TAB.content && (
        <AdminLandingContentEditor
          roadmapSteps={currentConfig.roadmapSteps}
          whyChooseFeatures={currentConfig.whyChooseFeatures}
          faqs={currentConfig.faqs}
          isSaving={isSaving}
          saveMessage={saveMessage}
          onRoadmapChange={onRoadmapChange}
          onRoadmapFeaturesTextChange={onRoadmapFeaturesTextChange}
          onFeatureChange={onFeatureChange}
          onFaqChange={onFaqChange}
          onAddFaq={onAddFaq}
          onRemoveFaq={onRemoveFaq}
          onSubmit={onSaveConfig}
        />
      )}

      {activeTab === ADMIN_CONFIG_TAB.consultations && (
        <AdminConsultationsTable
          items={consultations}
          isLoading={isLoadingConsultations}
          onStatusChange={onStatusChange}
        />
      )}
    </div>
  );
}
