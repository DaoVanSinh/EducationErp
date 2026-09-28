import { createQueryClient } from "@/app/providers/query-client";
import { SessionExpiryWatcher } from "@/app/providers/session-expiry-watcher";
import { MotionProvider } from "@/shared/ui/motion-provider";
import { QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { BrowserRouter } from "react-router-dom";

/** QueryClient tạo trong state: mỗi lần render lại không được dựng client mới, cache sẽ mất trắng. */
export function AppProviders({ children }: { readonly children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);

  return (
    <QueryClientProvider client={queryClient}>
      <MotionProvider>
        <BrowserRouter>
          <SessionExpiryWatcher />
          {children}
        </BrowserRouter>
      </MotionProvider>
    </QueryClientProvider>
  );
}
