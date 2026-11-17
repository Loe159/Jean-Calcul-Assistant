# Now Brief — recent notifications

The filtering summary and recent activity are intentionally separate.

- Filtering statistics come from `NotificationObservation`, which intentionally skips
  protected or rate-limited notifications (up to 1 inference/minute, 100/day).
- Recent activity comes from the existing on-device `NotificationHistoryRepository`
  filled by `AgentNotificationHistoryService.onNotificationPosted()`. It includes
  notifications that did not pass through the classifier, provided the listener
  permission was granted when the notification arrived.
- The repository retains at most 1,000 keys for 7 days. The UI shows the latest
  300 stored notifications, grouped by package and sorted by latest notification.
  These are received notification events, not necessarily currently active alerts.
- Application icons come directly from Android PackageManager and are never
  transmitted to a service.
- Expand an app to see individual entries. Swipe left or right on the app
  group (all stored entries) or a single entry to dismiss from this dashboard.
  Pressing the row's close button provides an accessible alternative.
- Dismissal changes the **local briefing history only** and never calls
  NotificationListenerService.cancelNotification. It therefore cannot silently
  delete a security message, reminder or other Android system notification.
- The dashboard runs no polling loop and reads SQLite via Dispatchers.IO when
  opened or when the listener posts an update. Grouping is deterministic and
  generated locally.

E2E uses synthetic records, checks grouping, expansion and a dismissal action.
