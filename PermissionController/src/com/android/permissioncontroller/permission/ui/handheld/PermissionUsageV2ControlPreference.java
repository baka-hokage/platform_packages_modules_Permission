/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.handheld;

import android.Manifest;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.KotlinUtils;

import java.util.List;

/**
 * Preference for the top level privacy hub page
 */
public class PermissionUsageV2ControlPreference extends Preference {
    private static final List<String> SENSOR_DATA_PERMISSIONS = List.of(
            Manifest.permission_group.LOCATION,
            Manifest.permission_group.CAMERA,
            Manifest.permission_group.MICROPHONE
    );

    private final Context mContext;
    private final String mGroupName;
    private final int mCount;

    public PermissionUsageV2ControlPreference(@NonNull Context context, @NonNull String groupName,
            int count) {
        super(context);
        mContext = context;
        mGroupName = groupName;
        mCount = count;

        CharSequence permGroupLabel = KotlinUtils.INSTANCE.getPermGroupLabel(mContext, mGroupName);
        setTitle(permGroupLabel);
        setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(mContext, mGroupName));
        setSummary(mContext.getResources().getQuantityString(
                R.plurals.permission_usage_preference_label, mCount, mCount));

        if (mCount == 0) {
            this.setEnabled(false);
        } else if (SENSOR_DATA_PERMISSIONS.contains(groupName)) {
            setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(Intent.ACTION_REVIEW_PERMISSION_HISTORY);
                intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mGroupName);

                mContext.startActivity(intent);
                return true;
            });
        } else {
            setOnPreferenceClickListener((preference) -> {
                Intent intent = new Intent(Intent.ACTION_MANAGE_PERMISSION_APPS);
                intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mGroupName);

                mContext.startActivity(intent);
                return true;
            });
        }
    }
}
