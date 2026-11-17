# Now Brief — recent notifications

The filtering summary and recent activity are intentionally separate.

- Filtering statistics come from `NotificationObservation` for the current Android
  process (up to 5 inferences/minute, 500/day). Empty statistics cards stay hidden.
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
  The 48dp row close button and the expanded group's bulk action provide accessible
  alternatives. An on-screen Undo restores removed local copies and decisions.
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
by application under **Suivi du filtrage**; the ordinary receipt feed is
separate. An unconfirmed proposal is never displayed as a successful delete.
The session-level classifier counters are unchanged.

Actual automatic permanent dismissal still requires the user's existing
explicit personalized-mode opt-in for **all eligible third-party apps**,
AD/SPAM category setting, advertisement score ≥0.40 with margin ≥0.08 or spam
score ≥0.65 with margin ≥0.20, live notification
existence/fingerprint, non-system/low-importance status, and sensitive-content
protections. The default modes do not permanently delete notifications.
The dashboard has a direct entry to those filtering settings. Notifications
skipped by these limits remain in recent activity rather than silently
pretending to be classified.

Touch on a notification card acquires a local horizontal-gesture lock before
the navigation scaffold receives the pointer drag. It releases on pointer-up
or disposal. The sticky swipe uses a monotonic curved motion profile (resistant
at the start, faster near the edge), the actual pinned `COUI-Kit` `rememberCouiTouchMotion` /
`Modifier.couiTouchMotion` deformation and rubber springs used by Menu,
`couiButtonFeedback` for the press mask, stretched edges,
and a damped spring on release; rightward swipe never opens the chat.


## All-app classification (V2 settings migration)

The mode 3 app/package allowlist and its input field were removed.
Every eligible third-party application now goes through the same local
policy; the model's classification determines whether to keep, snooze
or (after explicit global confirmation) permanently dismiss the Android
notification. Protected system apps, conversations, payments, OTP, alarms,
high-priority or non-clearable notifications remain exempt, even if the
classifier assigns an advertisement/spam label. All received notifications
continue to be stored in the local Now Brief history, regardless of whether
they were classified.

Previously saved v1 configuration is migrated to the v2 format without
package rules. If the old settings allowed irreversible deletion for only
specific apps, the action falls back to snoozing for 1 hour until the user
explicitly reconfirms the expanded **all-app** deletion scope.


## Why a notification may have no model decision (analysis queue)

The original model entry point dropped events silently when one of these
conditions held: the model was missing, the alert was protected, Android battery
saver was on, battery was below 20%, the previous inference was less than
60 seconds ago, or the daily limit had been reached. Since the history was
written before inference, all those events appeared as "Received, not analyzed"
without explaining why. This did not mean the listener had stopped working.

The new version assigns an explicit local status:
- MODEL_MISSING / PROTECTED / BATTERY_PAUSED / LIMIT_REACHED
- QUEUED: eligible, pending an inference slot
- ANALYSIS_ERROR: inference failed; its exception type only appears in
  the Settings diagnostic, not the notification text
- EXPIRED: notification left Android before the queued inference could run
- KEPT, OBSERVED, PROPOSED, SNOOZED_*, DISMISSED: actual decision/result.

At most 64 eligible notifications are queued in RAM, one inference can start
every 12 seconds, and no more than 500 inferences are started per day.
A delayed callback is scheduled only while this listener/process exists;
no additional service, network request or wake lock is introduced. If an
alert has disappeared before its turn, it is not acted upon; saved history
remains available. This means bursts might take several minutes and alerts
outside the queue or quota are not automatically processed.

On listener reconnection, up to 64 recent, still-active unclassified
notifications can be requeued. In Settings > Notifications intelligentes,
"Réessayer l'analyse des notifications actives" performs the same bounded
recovery on demand. "Diagnostic de l'analyse locale" reports installation,
listener access, power limits, queue count, daily quota and the last error
class without exposing any private notification text.
