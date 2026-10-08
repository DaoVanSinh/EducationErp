import { Spinner } from "@/shared/ui/spinner";

export function FullScreenLoader({ label = "Đang tải" }: { readonly label?: string }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3">
      <Spinner size={32} />
      <p className="text-sm font-medium text-slate-500">{label}</p>
    </div>
  );
}
