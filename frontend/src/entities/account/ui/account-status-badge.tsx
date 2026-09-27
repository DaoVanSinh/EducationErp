import {
  ACCOUNT_STATUS,
  ACCOUNT_STATUS_LABEL,
  type AccountStatus,
} from "@/entities/account/model/account-schema";
import { Badge } from "@/shared/ui/badge";

export function AccountStatusBadge({ status }: { readonly status: AccountStatus }) {
  return (
    <Badge tone={status === ACCOUNT_STATUS.active ? "positive" : "danger"}>
      {ACCOUNT_STATUS_LABEL[status]}
    </Badge>
  );
}
