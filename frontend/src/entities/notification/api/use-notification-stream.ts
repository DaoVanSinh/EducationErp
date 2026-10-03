import { NOTIFICATION_EVENT_TYPE } from "@/entities/notification/model/notification-schema";
import { useEffect } from "react";

const STREAM_PATH = "/api/notifications/stream";

/**
 * Cookie HttpOnly đi kèm tự động với EventSource cùng origin - không cần truyền token thủ công, giống
 * mọi request khác của app này (xem apiClient, cũng dựa vào cookie).
 *
 * Nhận callback thay vì tự import entities/account để gọi invalidateQueries: entity không được phụ
 * thuộc entity khác (boundaries/dependencies) - việc "đổi quyền thì làm mới phiên" là quyết định của
 * nơi gọi (app/router/require-auth.tsx), không phải của chính kênh thông báo này.
 */
export function useNotificationStream(onPermissionChanged: () => void): void {
  useEffect(() => {
    const source = new EventSource(STREAM_PATH, { withCredentials: true });

    source.addEventListener(NOTIFICATION_EVENT_TYPE.permissionChanged, onPermissionChanged);

    return () => {
      source.removeEventListener(NOTIFICATION_EVENT_TYPE.permissionChanged, onPermissionChanged);
      source.close();
    };
  }, [onPermissionChanged]);
}
