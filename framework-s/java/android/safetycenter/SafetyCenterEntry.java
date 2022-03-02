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

package android.safetycenter;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.PendingIntent;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.RequiresApi;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * An individual entry in the Safety Center.
 *
 * <p>A {@link SafetyCenterEntry} conveys the current status of an individual safety feature on the
 * device. Entries are present even if they have no associated active issues. In contrast, a
 * {@link SafetyCenterIssue} is ephemeral and disappears when the issue is resolved.
 *
 * <p>Entries link to their corresponding component or an action on it via {@link
 * #getPendingIntent()}.
 *
 * @hide
 */
@SystemApi
@RequiresApi(TIRAMISU)
public final class SafetyCenterEntry implements Parcelable {

    /**
     * All possible severity levels for a {@link SafetyCenterEntry}.
     *
     * @see SafetyCenterEntry#getSeverityLevel()
     * @see Builder#setSeverityLevel(int)
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "ENTRY_SEVERITY_LEVEL_", value = {
            ENTRY_SEVERITY_LEVEL_UNKNOWN,
            ENTRY_SEVERITY_LEVEL_NONE,
            ENTRY_SEVERITY_LEVEL_OK,
            ENTRY_SEVERITY_LEVEL_RECOMMENDATION,
            ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING,
    })
    public @interface EntrySeverityLevel {
    }

    /**
     * Indicates the severity level of this entry is not currently known. This may be because of an
     * error or because some information is missing.
     */
    public static final int ENTRY_SEVERITY_LEVEL_UNKNOWN = 3000;

    /**
     * Indicates this entry does not have a severity level.
     *
     * <p>This is used when the Safety Center has no opinion on the severity of this entry (e.g.
     * a security setting isn't configured but it's not considered a risk, or for privacy-related
     * entries).
     */
    public static final int ENTRY_SEVERITY_LEVEL_NONE = 3100;

    /** Indicates that there are no problems present with this entry. */
    public static final int ENTRY_SEVERITY_LEVEL_OK = 3200;

    /** Indicates there are safety recommendations for this entry. */
    public static final int ENTRY_SEVERITY_LEVEL_RECOMMENDATION = 3300;

    /** Indicates there are critical safety warnings for this entry. */
    public static final int ENTRY_SEVERITY_LEVEL_CRITICAL_WARNING = 3400;

    /**
     * All possible icon types for a {@link SafetyCenterEntry} to use when its severity level is
     * {@link #ENTRY_SEVERITY_LEVEL_NONE}.
     *
     * <p>It is only relevant when the entry's severity level is {@link #ENTRY_SEVERITY_LEVEL_NONE}.
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "SEVERITY_NONE_ICON_TYPE_", value = {
            SEVERITY_NONE_ICON_TYPE_NO_ICON,
            SEVERITY_NONE_ICON_TYPE_PRIVACY,
            SEVERITY_NONE_ICON_TYPE_NO_RECOMMENDATION,
    })
    public @interface SeverityNoneIconType {
    }

    /** Indicates an entry with {@link #ENTRY_SEVERITY_LEVEL_NONE} should not use an icon. */
    public static final int SEVERITY_NONE_ICON_TYPE_NO_ICON = 0;

    /**
     * Indicates an entry with {@link #ENTRY_SEVERITY_LEVEL_NONE} should use the privacy icon, for
     * privacy features.
     */
    public static final int SEVERITY_NONE_ICON_TYPE_PRIVACY = 1;

    /**
     * Indicates an entry with {@link #ENTRY_SEVERITY_LEVEL_NONE} should use an icon indicating it
     * has no current recommendation or is missing data to provide one.
     */
    public static final int SEVERITY_NONE_ICON_TYPE_NO_RECOMMENDATION = 2;


    @NonNull
    private final String mId;
    @NonNull
    private final CharSequence mTitle;
    @Nullable
    private final CharSequence mSummary;
    @EntrySeverityLevel
    private final int mSeverityLevel;
    @SeverityNoneIconType
    private final int mSeverityNoneIconType;
    private final boolean mEnabled;
    @NonNull
    private final PendingIntent mPendingIntent;
    @Nullable
    private final IconAction mIconAction;

    private SafetyCenterEntry(
            @NonNull String id,
            @NonNull CharSequence title,
            @Nullable CharSequence summary,
            @EntrySeverityLevel int severityLevel,
            @SeverityNoneIconType int severityNoneIconType,
            boolean enabled,
            @NonNull PendingIntent pendingIntent,
            @Nullable IconAction iconAction) {
        mId = requireNonNull(id);
        mTitle = requireNonNull(title);
        mSummary = summary;
        mSeverityLevel = severityLevel;
        mSeverityNoneIconType = severityNoneIconType;
        mEnabled = enabled;
        mPendingIntent = requireNonNull(pendingIntent);
        mIconAction = iconAction;
    }

    /**
     * Returns the encoded string ID which uniquely identifies this entry within the Safety Center
     * on the device for the current user across all profiles and accounts.
     */
    @NonNull
    public String getId() {
        return mId;
    }

    /** Returns the title that describes this entry. */
    @NonNull
    public CharSequence getTitle() {
        return mTitle;
    }

    /** Returns the summary text that describes this entry if present, or {@code null} otherwise. */
    @Nullable
    public CharSequence getSummary() {
        return mSummary;
    }

    /** Returns the {@link EntrySeverityLevel} of this entry. */
    @EntrySeverityLevel
    public int getSeverityLevel() {
        return mSeverityLevel;
    }

    /** Returns the {@link SeverityNoneIconType} of this entry. */
    @SeverityNoneIconType
    public int getSeverityNoneIconType() {
        return mSeverityNoneIconType;
    }

    /** Returns whether or not this entry is enabled. */
    public boolean isEnabled() {
        return mEnabled;
    }

    /** Returns the {@link PendingIntent} to execute when this entry is selected. */
    @NonNull
    public PendingIntent getPendingIntent() {
        return mPendingIntent;
    }

    /**
     * Returns the optional {@link IconAction} for this entry if present, or {@code null}
     * otherwise.
     */
    @Nullable
    public IconAction getIconAction() {
        return mIconAction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SafetyCenterEntry that = (SafetyCenterEntry) o;
        return mSeverityLevel == that.mSeverityLevel
                && mSeverityNoneIconType == that.mSeverityNoneIconType
                && mEnabled == that.mEnabled
                && Objects.equals(mId, that.mId)
                && TextUtils.equals(mTitle, that.mTitle)
                && TextUtils.equals(mSummary, that.mSummary)
                && Objects.equals(mPendingIntent, that.mPendingIntent)
                && Objects.equals(mIconAction, that.mIconAction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId, mTitle, mSummary, mSeverityLevel, mSeverityNoneIconType, mEnabled,
                mPendingIntent, mIconAction);
    }

    @Override
    public String toString() {
        return "SafetyCenterEntry{"
                + "mId='" + mId + '\''
                + ", mTitle=" + mTitle
                + ", mSummary=" + mSummary
                + ", mSeverityLevel=" + mSeverityLevel
                + ", mSeverityNoneIconType=" + mSeverityNoneIconType
                + ", mEnabled=" + mEnabled
                + ", mAction=" + mPendingIntent
                + ", mIconAction=" + mIconAction
                + '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(mId);
        TextUtils.writeToParcel(mTitle, dest, flags);
        TextUtils.writeToParcel(mSummary, dest, flags);
        dest.writeInt(mSeverityLevel);
        dest.writeInt(mSeverityNoneIconType);
        dest.writeBoolean(mEnabled);
        dest.writeParcelable(mPendingIntent, flags);
        dest.writeParcelable(mIconAction, flags);
    }

    @NonNull
    public static final Creator<SafetyCenterEntry> CREATOR = new Creator<SafetyCenterEntry>() {
        @Override
        public SafetyCenterEntry createFromParcel(Parcel in) {
            return new Builder(in.readString())
                    .setTitle(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSummary(TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in))
                    .setSeverityLevel(in.readInt())
                    .setSeverityNoneIconType(in.readInt())
                    .setEnabled(in.readBoolean())
                    .setPendingIntent(
                            in.readParcelable(
                                    PendingIntent.class.getClassLoader(), PendingIntent.class))
                    .setIconAction(
                            in.readParcelable(
                                    IconAction.class.getClassLoader(), IconAction.class))
                    .build();
        }

        @Override
        public SafetyCenterEntry[] newArray(int size) {
            return new SafetyCenterEntry[size];
        }
    };

    /** Builder class for {@link SafetyCenterEntry}. */
    public static final class Builder {
        @NonNull
        private String mId;
        private CharSequence mTitle;
        private CharSequence mSummary;
        @EntrySeverityLevel
        private int mSeverityLevel = ENTRY_SEVERITY_LEVEL_UNKNOWN;
        @SeverityNoneIconType
        private int mSeverityNoneIconType = SEVERITY_NONE_ICON_TYPE_NO_ICON;
        private boolean mEnabled = true;
        private PendingIntent mPendingIntent;
        private IconAction mIconAction;

        /**
         * Creates a {@link Builder} for a {@link SafetyCenterEntry}.
         *
         * @param id an encoded string ID to be returned by {@link #getId()}
         */
        public Builder(@NonNull String id) {
            mId = requireNonNull(id);
        }

        /**
         * Creates a pre-populated {@link Builder} with the values from the given {@link
         * SafetyCenterEntry}.
         */
        public Builder(@NonNull SafetyCenterEntry safetyCenterEntry) {
            mId = safetyCenterEntry.mId;
            mTitle = safetyCenterEntry.mTitle;
            mSummary = safetyCenterEntry.mSummary;
            mSeverityLevel = safetyCenterEntry.mSeverityLevel;
            mSeverityNoneIconType = safetyCenterEntry.mSeverityNoneIconType;
            mEnabled = safetyCenterEntry.mEnabled;
            mPendingIntent = safetyCenterEntry.mPendingIntent;
            mIconAction = safetyCenterEntry.mIconAction;
        }

        /** Sets the ID for this entry. Required. */
        @NonNull
        public Builder setId(@NonNull String id) {
            mId = requireNonNull(id);
            return this;
        }

        /** Sets the title for this entry. Required. */
        @NonNull
        public Builder setTitle(@NonNull CharSequence title) {
            mTitle = requireNonNull(title);
            return this;
        }

        /** Sets the optional summary text for this entry. */
        @NonNull
        public Builder setSummary(@Nullable CharSequence summary) {
            mSummary = summary;
            return this;
        }

        /**
         * Sets the {@link EntrySeverityLevel} for this entry. Defaults to {@link
         * #ENTRY_SEVERITY_LEVEL_UNKNOWN}.
         */
        @NonNull
        public Builder setSeverityLevel(@EntrySeverityLevel int severityLevel) {
            mSeverityLevel = severityLevel;
            return this;
        }

        /**
         * Sets the {@link SeverityNoneIconType} for this entry. Defaults to {@link
         * #SEVERITY_NONE_ICON_TYPE_NO_ICON}.
         */
        @NonNull
        public Builder setSeverityNoneIconType(@SeverityNoneIconType int severityNoneIconType) {
            mSeverityNoneIconType = severityNoneIconType;
            return this;
        }

        /** Sets whether or not this entry is enabled. Defaults to {@code true}. */
        @NonNull
        public Builder setEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }

        /** Sets the {@link PendingIntent} to execute when this entry is selected. Required. */
        @NonNull
        public Builder setPendingIntent(@NonNull PendingIntent pendingIntent) {
            mPendingIntent = requireNonNull(pendingIntent);
            return this;
        }

        /** Sets the optional {@link IconAction} for this entry. */
        @NonNull
        public Builder setIconAction(@Nullable IconAction iconAction) {
            mIconAction = iconAction;
            return this;
        }

        /** Sets the optional {@link IconAction} for this entry. */
        @NonNull
        public Builder setIconAction(
                @IconAction.IconActionType int type, @NonNull PendingIntent pendingIntent) {
            mIconAction = new IconAction(type, pendingIntent);
            return this;
        }

        /** Creates the {@link SafetyCenterEntry} defined by this {@link Builder}. */
        @NonNull
        public SafetyCenterEntry build() {
            return new SafetyCenterEntry(
                    mId,
                    mTitle,
                    mSummary,
                    mSeverityLevel,
                    mSeverityNoneIconType,
                    mEnabled,
                    mPendingIntent,
                    mIconAction);
        }
    }

    /** An optional additional action with an icon for a {@link SafetyCenterEntry}. */
    public static final class IconAction implements Parcelable {

        /**
         * All possible icon action types.
         * @hide
         */
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = "ICON_ACTION_TYPE_", value = {
                ICON_ACTION_TYPE_GEAR,
                ICON_ACTION_TYPE_INFO,
        })
        public @interface IconActionType {
        }

        /** A gear-type icon action, e.g. that links to a settings page for a specific entry. */
        public static final int ICON_ACTION_TYPE_GEAR = 30100;

        /**
         * An info-type icon action, e.g. that displays some additional detailed info about a
         * specific entry.
         */
        public static final int ICON_ACTION_TYPE_INFO = 30200;

        @IconActionType
        private final int mType;
        @NonNull
        private final PendingIntent mPendingIntent;

        /** Creates an icon action for a {@link SafetyCenterEntry}. */
        public IconAction(@IconActionType int type, @NonNull PendingIntent pendingIntent) {
            mType = type;
            mPendingIntent = requireNonNull(pendingIntent);
        }

        /** Returns the {@link IconActionType} of this icon action. */
        @IconActionType
        public int getType() {
            return mType;
        }

        /** Returns the {@link PendingIntent} to execute when this icon action is selected. */
        @NonNull
        public PendingIntent getPendingIntent() {
            return mPendingIntent;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IconAction that = (IconAction) o;
            return mType == that.mType && Objects.equals(mPendingIntent, that.mPendingIntent);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mType, mPendingIntent);
        }

        @Override
        public String toString() {
            return "IconAction{"
                    + "mType=" + mType
                    + ", mPendingIntent=" + mPendingIntent
                    + '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(mType);
            dest.writeParcelable(mPendingIntent, flags);
        }

        @NonNull
        public static final Creator<IconAction> CREATOR = new Creator<IconAction>() {
            @Override
            public IconAction createFromParcel(Parcel in) {
                return new IconAction(
                        in.readInt(),
                        in.readParcelable(
                                PendingIntent.class.getClassLoader(), PendingIntent.class));
            }

            @Override
            public IconAction[] newArray(int size) {
                return new IconAction[size];
            }
        };

    }

}
