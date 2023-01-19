/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Binder;
import android.os.UserHandle;
import android.safetycenter.SafetySourceIssue;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.modules.utils.build.SdkLevel;
import com.android.safetycenter.data.SafetyCenterIssueDismissalRepository;
import com.android.safetycenter.data.SafetyCenterIssueRepository;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Class responsible for posting, updating and dismissing Safety Center notifications each time
 * Safety Center's issues change.
 *
 * <p>This class isn't thread safe. Thread safety must be handled by the caller.
 */
@RequiresApi(TIRAMISU)
@NotThreadSafe
final class SafetyCenterNotificationSender {

    private static final String TAG = "SafetyCenterNS";

    // We use a fixed notification ID because notifications are keyed by (tag, id) and it easier
    // to differentiate our notifications using the tag
    private static final int FIXED_NOTIFICATION_ID = 2345;

    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_NEVER = 100;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED = 200;
    private static final int NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY = 300;

    /**
     * Internal notification behavior {@code @IntDef} which is related to the {@code
     * SafetySourceIssue.NotificationBehavior} type introduced in Android U.
     *
     * <p>This definition is available on T+.
     *
     * <p>Unlike the U+/external {@code @IntDef}, this one has no "unspecified behavior" value. Any
     * issues which have unspecified behavior are resolved to one of these specific behaviors based
     * on their other properties.
     */
    @IntDef(
            prefix = {"NOTIFICATION_BEHAVIOR_INTERNAL"},
            value = {
                NOTIFICATION_BEHAVIOR_INTERNAL_NEVER,
                NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED,
                NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY
            })
    @Retention(RetentionPolicy.SOURCE)
    private @interface NotificationBehaviorInternal {}

    @NonNull private final Context mContext;

    @NonNull private final SafetyCenterNotificationFactory mNotificationFactory;

    @NonNull
    private final SafetyCenterIssueDismissalRepository mSafetyCenterIssueDismissalRepository;

    @NonNull private final SafetyCenterIssueRepository mSafetyCenterIssueRepository;

    private final ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> mNotifiedIssues =
            new ArrayMap<>();

    SafetyCenterNotificationSender(
            @NonNull Context context,
            @NonNull SafetyCenterNotificationFactory notificationFactory,
            @NonNull SafetyCenterIssueDismissalRepository safetyCenterIssueDismissalRepository,
            @NonNull SafetyCenterIssueRepository safetyCenterIssueRepository) {
        mContext = context;
        mNotificationFactory = notificationFactory;
        mSafetyCenterIssueDismissalRepository = safetyCenterIssueDismissalRepository;
        mSafetyCenterIssueRepository = safetyCenterIssueRepository;
    }

    /**
     * Updates Safety Center notifications, usually in response to a change in the issues for the
     * given userId.
     */
    void updateNotifications(@UserIdInt int userId) {
        if (!SafetyCenterFlags.getNotificationsEnabled()) {
            return;
        }

        NotificationManager notificationManager = getNotificationManagerForUser(userId);

        if (notificationManager == null) {
            Log.w(TAG, "Could not retrieve NotificationManager for user " + userId);
            return;
        }

        ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> issuesToNotify =
                getIssuesToNotify(userId);

        // Post or update notifications for notifiable issues, depending on their behavior,
        // keeping track of which issues notifications were posted/updated for:
        ArraySet<SafetyCenterIssueKey> freshIssueKeys = new ArraySet<>();
        for (int i = 0; i < issuesToNotify.size(); i++) {
            SafetyCenterIssueKey issueKey = issuesToNotify.keyAt(i);
            SafetySourceIssue issue = issuesToNotify.valueAt(i);

            boolean unchanged = issue.equals(mNotifiedIssues.get(issueKey));
            if (unchanged) {
                freshIssueKeys.add(issueKey);
                continue;
            }

            boolean wasPosted = postNotificationForIssue(notificationManager, issue, issueKey);
            if (wasPosted) {
                freshIssueKeys.add(issueKey);
            }
        }

        // Cancel previously-posted notifications, for this user, which were not just updated:
        cancelStaleNotifications(notificationManager, userId, freshIssueKeys);
    }

    /** Cancels all notifications previously posted by this class */
    void cancelAllNotifications() {
        // Loop in reverse index order to be able to remove entries while iterating
        for (int i = mNotifiedIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey issueKey = mNotifiedIssues.keyAt(i);
            cancelNotificationFromSystem(
                    getNotificationManagerForUser(issueKey.getUserId()),
                    getNotificationTag(issueKey));
            mNotifiedIssues.removeAt(i);
        }
    }

    /** Dumps state for debugging purposes. */
    void dump(@NonNull PrintWriter fout) {
        int notifiedIssuesCount = mNotifiedIssues.size();
        fout.println("NOTIFICATION SENDER (" + notifiedIssuesCount + " notified issues)");
        for (int i = 0; i < notifiedIssuesCount; i++) {
            SafetyCenterIssueKey key = mNotifiedIssues.keyAt(i);
            SafetySourceIssue issue = mNotifiedIssues.valueAt(i);
            fout.println("\t[" + i + "] " + toUserFriendlyString(key) + " -> " + issue);
        }
        fout.println();
    }

    /** Get all of the key-issue pairs for which notifications should be posted or updated now. */
    @NonNull
    private ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> getIssuesToNotify(
            @UserIdInt int userId) {
        ArrayMap<SafetyCenterIssueKey, SafetySourceIssue> result = new ArrayMap<>();
        List<SafetySourceIssueInfo> allIssuesInfo =
                mSafetyCenterIssueRepository.getIssuesForUser(userId);

        Duration minNotificationsDelay = SafetyCenterFlags.getNotificationsMinDelay();

        for (int i = 0; i < allIssuesInfo.size(); i++) {
            SafetySourceIssueInfo issueInfo = allIssuesInfo.get(i);
            SafetyCenterIssueKey issueKey = issueInfo.getSafetyCenterIssueKey();

            if (!areNotificationsAllowed(issueInfo)) {
                // Notifications are not allowed for this source
                continue;
            }

            // TODO(b/259084807): Consider extracting this policy to a separate class
            Instant dismissedAt =
                    mSafetyCenterIssueDismissalRepository.getNotificationDismissedAt(issueKey);
            if (dismissedAt != null) {
                // Notification for issue was previously dismissed and is skipped
                continue;
            }

            // Now retrieve the issue itself and use it to determine the behavior:
            SafetySourceIssue issue = issueInfo.getSafetySourceIssue();
            int behavior = getBehavior(issue, issueKey);
            if (behavior == NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY) {
                result.put(issueKey, issue);
            } else if (behavior == NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED) {
                Instant delayedNotificationTime =
                        mSafetyCenterIssueDismissalRepository
                                .getIssueFirstSeenAt(issueKey)
                                .plus(minNotificationsDelay);
                if (Instant.now().isAfter(delayedNotificationTime)) {
                    result.put(issueKey, issue);
                }
                // TODO(b/259094736): else handle delayed notifications using a scheduled job
            }
        }
        return result;
    }

    @NotificationBehaviorInternal
    private int getBehavior(
            @NonNull SafetySourceIssue issue, @NonNull SafetyCenterIssueKey issueKey) {
        if (SdkLevel.isAtLeastU()) {
            switch (issue.getNotificationBehavior()) {
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_NEVER:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_NEVER;
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_DELAYED:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_DELAYED;
                case SafetySourceIssue.NOTIFICATION_BEHAVIOR_IMMEDIATELY:
                    return NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY;
            }
        }
        // On Android T all issues are assumed to have "unspecified" behavior
        return getBehaviorForIssueWithUnspecifiedBehavior(issueKey);
    }

    @NotificationBehaviorInternal
    private int getBehaviorForIssueWithUnspecifiedBehavior(@NonNull SafetyCenterIssueKey issueKey) {
        // TODO(b/259083775): Make this implementation more useful/complex
        return NOTIFICATION_BEHAVIOR_INTERNAL_IMMEDIATELY;
    }

    private boolean areNotificationsAllowed(@NonNull SafetySourceIssueInfo issueInfo) {
        if (SdkLevel.isAtLeastU()) {
            if (issueInfo.getSafetySource().areNotificationsAllowed()) {
                return true;
            }
        }
        return SafetyCenterFlags.getNotificationsAllowedSourceIds()
                .contains(issueInfo.getSafetySource().getId());
    }

    private boolean postNotificationForIssue(
            @NonNull NotificationManager notificationManager,
            @NonNull SafetySourceIssue safetySourceIssue,
            @NonNull SafetyCenterIssueKey key) {
        Notification notification =
                mNotificationFactory.newNotificationForIssue(
                        notificationManager, safetySourceIssue, key);
        if (notification == null) {
            // Could not make a Notification for this issue!
            return false;
        }
        // The fixed notification ID is OK because notifications are keyed by (tag, id)
        String tag = getNotificationTag(key);
        boolean wasPosted = notifyFromSystem(notificationManager, tag, notification);
        if (wasPosted) {
            mNotifiedIssues.put(key, safetySourceIssue);
            return true;
        } else {
            return false;
        }
    }

    private void cancelStaleNotifications(
            @NonNull NotificationManager notificationManager,
            @UserIdInt int userId,
            @NonNull ArraySet<SafetyCenterIssueKey> freshIssueKeys) {
        // Loop in reverse index order to be able to remove entries while iterating
        for (int i = mNotifiedIssues.size() - 1; i >= 0; i--) {
            SafetyCenterIssueKey key = mNotifiedIssues.keyAt(i);
            if (key.getUserId() == userId && !freshIssueKeys.contains(key)) {
                // Notification should no longer be shown
                String tag = getNotificationTag(key);
                cancelNotificationFromSystem(notificationManager, tag);
                mNotifiedIssues.removeAt(i);
            }
        }
    }

    @NonNull
    private static String getNotificationTag(@NonNull SafetyCenterIssueKey issueKey) {
        // TODO(b/259084094): Make this tag creation more robust
        return issueKey.getSafetySourceId() + ":" + issueKey.getSafetySourceIssueId();
    }

    /** Returns a {@link NotificationManager} which will send notifications to the given user. */
    @Nullable
    private NotificationManager getNotificationManagerForUser(@UserIdInt int recipientUserId) {
        Context contextAsUser = mContext.createContextAsUser(UserHandle.of(recipientUserId), 0);
        return contextAsUser.getSystemService(NotificationManager.class);
    }

    /**
     * Sends a {@link Notification} from the system, dropping any calling identity. Returns {@code
     * true} if successful or {@code false} otherwise.
     *
     * <p>The recipient of the notification depends on the {@link Context} of the given {@link
     * NotificationManager}. Use {@link #getNotificationManagerForUser(int)} to send notifications
     * to a specific user.
     */
    private boolean notifyFromSystem(
            @NonNull NotificationManager notificationManager,
            @Nullable String tag,
            @NonNull Notification notification) {
        // This call is needed to send a notification from the system and this also grants the
        // necessary POST_NOTIFICATIONS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.notify(tag, FIXED_NOTIFICATION_ID, notification);
            return true;
        } catch (Throwable e) {
            Log.w(TAG, "Unable to send system notification", e);
            return false;
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /**
     * Cancels a {@link Notification} from the system, dropping any calling identity.
     *
     * <p>The recipient of the notification depends on the {@link Context} of the given {@link
     * NotificationManager}. Use {@link #getNotificationManagerForUser(int)} to cancel notifications
     * sent to a specific user.
     */
    private void cancelNotificationFromSystem(
            @NonNull NotificationManager notificationManager, @Nullable String tag) {
        // This call is needed to cancel a notification previously sent from the system
        final long callingId = Binder.clearCallingIdentity();
        try {
            notificationManager.cancel(tag, FIXED_NOTIFICATION_ID);
        } catch (Throwable e) {
            Log.w(TAG, "Unable to cancel system notification", e);
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }
}
