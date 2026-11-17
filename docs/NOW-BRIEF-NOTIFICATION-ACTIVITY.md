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


## Classification and Android actions (V2)

On `onNotificationPosted`, the listener saves the received alert locally.
When the locally installed multilingual NLI classifier runs, it updates the
same SQLite record to `KEPT`, `OBSERVED` or `PROPOSED` with the top label
and score (no network calls). The configured policy, safety guards and a
second live recheck determine whether Android is asked to snooze or dismiss.
**Only Android's `onNotificationRemoved` callback with the expected reason**
marks the record as `SNOOZED_1H`, `SNOOZED_24H` or `DISMISSED`.
The record remains available in the local seven-day history and is grouped
by application under **À vérifier / Supprimées**; the ordinary receipt feed is
separate. An unconfirmed proposal is never displayed as a successful delete.
The session-level classifier counters are unchanged.

Actual automatic permanent dismissal still requires the user's existing
explicit personalized-mode opt-in, allowed application, AD/SPAM category
setting, ≥0.999 classifier score, ≥0.50 label margin, live notification
existence/fingerprint, non-system/low-importance status, and sensitive-content
protections. The default modes do not permanently delete notifications.
The dashboard has a direct entry to those filtering settings and states the
power-saving classification limit (1 per minute; 100 per day). Notifications
skipped by these limits remain in recent activity rather than silently
pretending to be classified.

Touch on a notification card acquires a local horizontal-gesture lock before
the navigation scaffold receives the pointer drag. It releases on pointer-up
or disposal. The sticky swipe uses a monotonic curved motion profile (resistant
at the start, faster near the edge), the actual pinned `COUI-Kit` `rememberCouiTouchMotion` /
`Modifier.couiTouchMotion` deformation and rubber springs used by Menu,
`couiButtonFeedback` for the press mask, stretched edges,
and a damped spring on release; rightward swipe never opens the chat.
