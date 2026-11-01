import {
  consultationItemSchema,
  landingContentConfigSchema,
  landingOverviewSchema,
  type ConsultationItem,
  type ConsultationStatus,
  type LandingContentConfig,
  type LandingOverview,
  type SubmitConsultationPayload,
} from "@/modules/landing/model/landing-schema";
import { apiClient } from "@/shared/api/api-client";
import { API_ROUTE } from "@/shared/constants/api-routes";
import { z } from "zod";

const consultationListSchema = z.array(consultationItemSchema);

export const landingApi = {
  async getOverview(): Promise<LandingOverview> {
    const raw = await apiClient.get<unknown>(API_ROUTE.landing.overview);
    return landingOverviewSchema.parse(raw);
  },

  async submitConsultation(payload: SubmitConsultationPayload): Promise<string> {
    return apiClient.post<string>(API_ROUTE.landing.consultations, payload);
  },

  async getAdminConfig(): Promise<LandingContentConfig> {
    const raw = await apiClient.get<unknown>(API_ROUTE.landing.adminConfig);
    return landingContentConfigSchema.parse(raw);
  },

  async updateAdminConfig(payload: LandingContentConfig): Promise<LandingContentConfig> {
    const raw = await apiClient.patch<unknown>(API_ROUTE.landing.adminConfig, payload);
    return landingContentConfigSchema.parse(raw);
  },

  async listConsultations(): Promise<ConsultationItem[]> {
    const raw = await apiClient.get<unknown>(API_ROUTE.landing.adminConsultations);
    return consultationListSchema.parse(raw);
  },

  async updateConsultationStatus(id: string, status: ConsultationStatus): Promise<void> {
    await apiClient.patch<void>(API_ROUTE.landing.adminConsultationById(id), { status });
  },
} as const;
