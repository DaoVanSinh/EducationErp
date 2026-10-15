import { accountKeys } from "@/entities/account";
import { apiClient } from "@/shared/api/api-client";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

export function SessionExpiryWatcher() {
  const queryClient = useQueryClient();

  useEffect(
    () =>
      apiClient.onSessionExpired(() => {
        queueMicrotask(() => {
          const currentSession = queryClient.getQueryData(accountKeys.session());
          if (currentSession != null) {
            queryClient.clear();
            queryClient.setQueryData(accountKeys.session(), null);
          }
        });
      }),
    [queryClient],
  );

  return null;
}
