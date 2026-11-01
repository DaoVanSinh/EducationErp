import { landingApi } from "@/modules/landing/api/landing-api";
import { landingKeys } from "@/modules/landing/api/landing-keys";
import type {
  ConsultationStatus,
  LandingContentConfig,
  SubmitConsultationPayload,
} from "@/modules/landing/model/landing-schema";
import { QUERY_STALE_TIME_MS } from "@/shared/constants/query-config";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

export function useLandingOverview() {
  return useQuery({
    queryKey: landingKeys.overview(),
    queryFn: () => landingApi.getOverview(),
    staleTime: QUERY_STALE_TIME_MS.catalog,
  });
}

export function useSubmitConsultation() {
  return useMutation({
    mutationFn: (payload: SubmitConsultationPayload) => landingApi.submitConsultation(payload),
  });
}

export function useLandingAdminConfig() {
  return useQuery({
    queryKey: landingKeys.adminConfig(),
    queryFn: () => landingApi.getAdminConfig(),
    staleTime: QUERY_STALE_TIME_MS.catalog,
  });
}

export function useUpdateLandingConfig() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: LandingContentConfig) => landingApi.updateAdminConfig(payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: landingKeys.adminConfig() }),
        queryClient.invalidateQueries({ queryKey: landingKeys.overview() }),
      ]);
    },
  });
}

export function useLandingConsultations() {
  return useQuery({
    queryKey: landingKeys.consultations(),
    queryFn: () => landingApi.listConsultations(),
  });
}

export function useUpdateConsultationStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (args: { id: string; status: ConsultationStatus }) =>
      landingApi.updateConsultationStatus(args.id, args.status),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: landingKeys.consultations() });
    },
  });
}
