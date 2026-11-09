import {
  ACCOUNT_STATUS,
  ACCOUNT_STATUS_LABEL,
  type AccountStatus,
} from "@/entities/account/model/account-schema";
import { Badge } from "@/shared/ui/badge";

const STATUS_TONE = {
  [ACCOUNT_STATUS.active]: "positive",
  [ACCOUNT_STATUS.disabled]: "warning",
  [ACCOUNT_STATUS.closed]: "neutral",
} as const;

export function AccountStatusBadge({ status }: { readonly status: AccountStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{ACCOUNT_STATUS_LABEL[status]}</Badge>;
}
