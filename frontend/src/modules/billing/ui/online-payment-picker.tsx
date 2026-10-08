import { ONLINE_GATEWAYS, PAYMENT_METHOD_LABEL, type OnlineGateway } from "@/entities/billing";
import { GlassButton } from "@/shared/ui/glass-button";
import { CreditCard } from "lucide-react";

export interface OnlinePaymentPickerProps {
  readonly onPay: (gateway: OnlineGateway) => void;
  readonly isPaying: boolean;
}

/** Một nút cho mỗi cổng - đơn giản và rõ hơn một dropdown rồi phải bấm nút thứ hai. */
export function OnlinePaymentPicker({ onPay, isPaying }: OnlinePaymentPickerProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {ONLINE_GATEWAYS.map((gateway) => (
        <GlassButton
          key={gateway}
          size="sm"
          disabled={isPaying}
          onClick={() => onPay(gateway)}
          icon={<CreditCard size={14} aria-hidden />}
        >
          Thu qua {PAYMENT_METHOD_LABEL[gateway]}
        </GlassButton>
      ))}
    </div>
  );
}
