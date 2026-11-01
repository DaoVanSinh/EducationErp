import {
  useLandingAdminConfig,
  useLandingConsultations,
  useUpdateConsultationStatus,
  useUpdateLandingConfig,
} from "@/modules/landing/api/use-landing-overview";
import type {
  ConsultationStatus,
  FaqItemData,
  LandingCenterInfo,
  LandingContentConfig,
  RoadmapStep,
  WhyChooseFeature,
} from "@/modules/landing/model/landing-schema";
import { useState, type ChangeEvent, type FormEvent } from "react";

export const ADMIN_CONFIG_TAB = {
  info: "info",
  content: "content",
  consultations: "consultations",
} as const;

export type AdminConfigTabId = (typeof ADMIN_CONFIG_TAB)[keyof typeof ADMIN_CONFIG_TAB];

export function useCenterConfigController() {
  const configQuery = useLandingAdminConfig();
  const consultationsQuery = useLandingConsultations();
  const updateConfigMutation = useUpdateLandingConfig();
  const updateStatusMutation = useUpdateConsultationStatus();

  const [activeTab, setActiveTab] = useState<AdminConfigTabId>(ADMIN_CONFIG_TAB.info);
  const [draftConfig, setDraftConfig] = useState<LandingContentConfig | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  const currentConfig = draftConfig ?? configQuery.data ?? null;

  const updateDraft = (updater: (base: LandingContentConfig) => LandingContentConfig) => {
    const base = draftConfig ?? configQuery.data;
    if (!base) return;
    setSaveMessage(null);
    setDraftConfig(updater(base));
  };

  const handleCenterInfoChange = (
    event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    const { name, value } = event.target;
    updateDraft((base) => ({
      ...base,
      centerInfo: {
        ...base.centerInfo,
        [name as keyof LandingCenterInfo]: value,
      },
    }));
  };

  const handleRoadmapChange = (
    index: number,
    field: keyof RoadmapStep,
    value: string | string[],
  ) => {
    updateDraft((base) => ({
      ...base,
      roadmapSteps: base.roadmapSteps.map((step, i) =>
        i === index ? { ...step, [field]: value } : step,
      ),
    }));
  };

  const handleRoadmapFeaturesTextChange = (index: number, rawText: string) => {
    const parsed = rawText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
    handleRoadmapChange(index, "features", parsed);
  };

  const handleFeatureChange = (
    index: number,
    field: keyof WhyChooseFeature,
    value: string,
  ) => {
    updateDraft((base) => ({
      ...base,
      whyChooseFeatures: base.whyChooseFeatures.map((feat, i) =>
        i === index ? { ...feat, [field]: value } : feat,
      ),
    }));
  };

  const handleFaqChange = (index: number, field: keyof FaqItemData, value: string) => {
    updateDraft((base) => ({
      ...base,
      faqs: base.faqs.map((faq, i) => (i === index ? { ...faq, [field]: value } : faq)),
    }));
  };

  const handleAddFaq = () => {
    updateDraft((base) => ({
      ...base,
      faqs: [
        ...base.faqs,
        {
          question: "Câu hỏi tuyển sinh mới?",
          answer: "Nhập câu trả lời chi tiết tại đây.",
        },
      ],
    }));
  };

  const handleRemoveFaq = (index: number) => {
    updateDraft((base) => ({
      ...base,
      faqs: base.faqs.filter((_, i) => i !== index),
    }));
  };

  const handleSaveConfig = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!currentConfig) return;
    setSaveMessage(null);
    updateConfigMutation.mutate(currentConfig, {
      onSuccess: (updated) => {
        setDraftConfig(updated);
        setSaveMessage("Đã lưu cấu hình thông tin trung tâm & Landing Page thành công!");
      },
    });
  };

  const handleStatusChange = (id: string, status: ConsultationStatus) => {
    updateStatusMutation.mutate({ id, status });
  };

  return {
    activeTab,
    currentConfig,
    consultations: consultationsQuery.data ?? [],
    isLoadingConfig: configQuery.isLoading,
    isLoadingConsultations: consultationsQuery.isLoading,
    isSaving: updateConfigMutation.isPending,
    saveMessage,
    onSelectTab: setActiveTab,
    onCenterInfoChange: handleCenterInfoChange,
    onRoadmapChange: handleRoadmapChange,
    onRoadmapFeaturesTextChange: handleRoadmapFeaturesTextChange,
    onFeatureChange: handleFeatureChange,
    onFaqChange: handleFaqChange,
    onAddFaq: handleAddFaq,
    onRemoveFaq: handleRemoveFaq,
    onSaveConfig: handleSaveConfig,
    onStatusChange: handleStatusChange,
  };
}
