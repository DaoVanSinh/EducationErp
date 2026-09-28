import { Spinner } from "@/shared/ui/spinner";

export function FullScreenLoader({ label = "Đang tải" }: { readonly label?: string }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-3">
      <Spinner size={28} />
      <p className="text-sm text-mist-400">{label}</p>
    </div>
  );
}
