export const NOTIFICATION_EVENT_TYPE = {
  permissionChanged: "PERMISSION_CHANGED",
} as const;

export type NotificationEventType = (typeof NOTIFICATION_EVENT_TYPE)[keyof typeof NOTIFICATION_EVENT_TYPE];
