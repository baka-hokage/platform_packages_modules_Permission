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

package com.android.permissioncontroller.permission.debug;

import static java.lang.annotation.RetentionPolicy.SOURCE;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.AppPermissionUsage.GroupUsage;
import com.android.permissioncontroller.permission.model.legacy.PermissionApps;
import com.android.permissioncontroller.permission.ui.handheld.PermissionUsageV2ControlPreference;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.ActionBarShadowController;

import java.lang.annotation.Retention;
import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is a V2 version of the permission usage page. WIP.
 */
public class PermissionUsageV2Fragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {
    private static final String LOG_TAG = "PermUsageV2Fragment";

    @Retention(SOURCE)
    @IntDef(value = {SORT_RECENT, SORT_RECENT_APPS})
    @interface SortOption {}
    static final int SORT_RECENT = 1;
    static final int SORT_RECENT_APPS = 2;

    public static final int FILTER_24_HOURS = 2;
    private static final int MENU_SORT_BY_APP = MENU_HIDE_SYSTEM + 1;
    private static final int MENU_SORT_BY_TIME = MENU_HIDE_SYSTEM + 2;
    private static final int MENU_FILTER_BY_PERMISSIONS = MENU_HIDE_SYSTEM + 3;
    private static final int MENU_FILTER_BY_TIME = MENU_HIDE_SYSTEM + 4;
    private static final int MENU_REFRESH = MENU_HIDE_SYSTEM + 5;

    private static final String KEY_SHOW_SYSTEM_PREFS = "_show_system";
    private static final String SHOW_SYSTEM_KEY = PermissionUsageV2Fragment.class.getName()
            + KEY_SHOW_SYSTEM_PREFS;
    private static final String KEY_PERM_NAME = "_perm_name";
    private static final String PERM_NAME_KEY = PermissionUsageV2Fragment.class.getName()
            + KEY_PERM_NAME;
    private static final String KEY_TIME_INDEX = "_time_index";
    private static final String TIME_INDEX_KEY = PermissionUsageV2Fragment.class.getName()
            + KEY_TIME_INDEX;
    private static final String KEY_SORT = "_sort";
    private static final String SORT_KEY = PermissionUsageV2Fragment.class.getName()
            + KEY_SORT;

    private static final Map<String, Integer> PERMISSION_GROUP_ORDER = Map.of(
            Manifest.permission_group.LOCATION, 0,
            Manifest.permission_group.CAMERA, 1,
            Manifest.permission_group.MICROPHONE, 2
    );
    private static final int DEFAULT_ORDER = 3;

    private @NonNull PermissionUsages mPermissionUsages;
    private @Nullable List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();

    private Collator mCollator;

    private @NonNull List<TimeFilterItem> mFilterTimes;
    private int mFilterTimeIndex;
    private String mFilterGroup;
    private @PermissionUsageV2Fragment.SortOption int mSort;

    private boolean mShowSystem;
    private boolean mHasSystemApps;
    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private MenuItem mSortByApp;
    private MenuItem mSortByTime;

    private ArrayMap<String, Integer> mGroupAppCounts = new ArrayMap<>();

    private boolean mFinishedInitialLoad;

    /**
     * @return A new fragment
     */
    public static @NonNull PermissionUsageV2Fragment newInstance(@Nullable String groupName,
            long numMillis) {
        PermissionUsageV2Fragment fragment = new PermissionUsageV2Fragment();
        Bundle arguments = new Bundle();
        if (groupName != null) {
            arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
        }
        arguments.putLong(Intent.EXTRA_DURATION_MILLIS, numMillis);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFinishedInitialLoad = false;
        mSort = SORT_RECENT_APPS;
        mFilterGroup = null;
        initializeTimeFilter();
        mFilterTimeIndex = FILTER_24_HOURS;

        if (savedInstanceState != null) {
            mShowSystem = savedInstanceState.getBoolean(SHOW_SYSTEM_KEY);
            mFilterGroup = savedInstanceState.getString(PERM_NAME_KEY);
            mFilterTimeIndex = savedInstanceState.getInt(TIME_INDEX_KEY);
            mSort = savedInstanceState.getInt(SORT_KEY);
        }

        setLoading(true, false);
        setHasOptionsMenu(true);
        ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        if (mFilterGroup == null) {
            mFilterGroup = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        }

        Context context = getPreferenceManager().getContext();
        mCollator = Collator.getInstance(
                context.getResources().getConfiguration().getLocales().get(0));
        mPermissionUsages = new PermissionUsages(context);

        reloadData();
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().setTitle(R.string.permission_usage_title);
    }

    /**
     * Initialize the time filter to show the smallest entry greater than the time passed in as an
     * argument.  If nothing is passed, this simply initializes the possible values.
     */
    private void initializeTimeFilter() {
        Context context = getPreferenceManager().getContext();
        mFilterTimes = new ArrayList<>();
        mFilterTimes.add(new TimeFilterItem(Long.MAX_VALUE,
                context.getString(R.string.permission_usage_any_time)));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(7),
                context.getString(R.string.permission_usage_last_7_days)));
        mFilterTimes.add(new TimeFilterItem(DAYS.toMillis(1),
                context.getString(R.string.permission_usage_last_day)));
        mFilterTimes.add(new TimeFilterItem(HOURS.toMillis(1),
                context.getString(R.string.permission_usage_last_hour)));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(15),
                context.getString(R.string.permission_usage_last_15_minutes)));
        mFilterTimes.add(new TimeFilterItem(MINUTES.toMillis(1),
                context.getString(R.string.permission_usage_last_minute)));
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SHOW_SYSTEM_KEY, mShowSystem);
        outState.putString(PERM_NAME_KEY, mFilterGroup);
        outState.putInt(TIME_INDEX_KEY, mFilterTimeIndex);
        outState.putInt(SORT_KEY, mSort);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        mSortByApp = menu.add(Menu.NONE, MENU_SORT_BY_APP, Menu.NONE, R.string.sort_by_app);
        mSortByTime = menu.add(Menu.NONE, MENU_SORT_BY_TIME, Menu.NONE, R.string.sort_by_time);
        menu.add(Menu.NONE, MENU_FILTER_BY_PERMISSIONS, Menu.NONE, R.string.filter_by_permissions);
        menu.add(Menu.NONE, MENU_FILTER_BY_TIME, Menu.NONE, R.string.filter_by_time);
        if (mHasSystemApps) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
        }

        HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_permission_usage,
                getClass().getName());
        MenuItem refresh = menu.add(Menu.NONE, MENU_REFRESH, Menu.NONE,
                R.string.permission_usage_refresh);
        refresh.setIcon(R.drawable.ic_refresh);
        refresh.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        updateMenu();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getActivity().finish();
                return true;
            case MENU_SORT_BY_APP:
                mSort = SORT_RECENT_APPS;
                updateUI();
                updateMenu();
                break;
            case MENU_SORT_BY_TIME:
                mSort = SORT_RECENT;
                updateUI();
                updateMenu();
                break;
            case MENU_FILTER_BY_PERMISSIONS:
                showPermissionFilterDialog();
                break;
            case MENU_FILTER_BY_TIME:
                showTimeFilterDialog();
                break;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mShowSystem = item.getItemId() == MENU_SHOW_SYSTEM;
                // We already loaded all data, so don't reload
                updateUI();
                updateMenu();
                break;
            case MENU_REFRESH:
                reloadData();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu() {
        if (mHasSystemApps) {
            /* Do not show system apps for now
                mShowSystemMenu.setVisible(!mShowSystem);
                mHideSystemMenu.setVisible(mShowSystem);
             */
            mShowSystemMenu.setVisible(false);
            mHideSystemMenu.setVisible(false);
        }

        mSortByApp.setVisible(mSort != SORT_RECENT_APPS);
        mSortByTime.setVisible(mSort != SORT_RECENT);
    }

    @Override
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());

        // Ensure the group name is valid.
        if (getGroup(mFilterGroup) == null) {
            mFilterGroup = null;
        }

        updateUI();
    }

    @Override
    public int getEmptyViewString() {
        return R.string.no_permission_usages;
    }

    private void updateUI() {
        if (mAppPermissionUsages.isEmpty() || getActivity() == null) {
            return;
        }
        Context context = getActivity();

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(context);
            setPreferenceScreen(screen);
        }
        screen.removeAll();

        StringBuffer accounts = new StringBuffer();
        for (UserHandle user : getContext().getSystemService(UserManager.class).getAllProfiles()) {
            for (Account account : getContext().createContextAsUser(user, 0).getSystemService(
                    AccountManager.class).getAccounts()) {
                accounts.append(", " + account.name);
            }
        }
        if (accounts.length() > 0) {
            accounts.delete(0, 2);
        }

        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        long curTime = System.currentTimeMillis();
        long startTime = Math.max(timeFilterItem == null ? 0 : (curTime - timeFilterItem.getTime()),
                Instant.EPOCH.toEpochMilli());

        mGroupAppCounts.clear();
        // Permission group to count mapping
        Map<String, Integer> usages = new HashMap<>();
        List<AppPermissionGroup> permissionGroups = getOSPermissionGroups();
        for (int i = 0; i < permissionGroups.size(); i++) {
            usages.put(permissionGroups.get(i).getName(), 0);
        }
        ArrayList<PermissionApps.PermissionApp> permApps = new ArrayList<>();

        boolean seenSystemApp = extractPermissionUsage(usages, permApps, startTime);

        if (mHasSystemApps != seenSystemApp) {
            mHasSystemApps = seenSystemApp;
            getActivity().invalidateOptionsMenu();
        }

        AppPermissionGroup group = getGroup(mFilterGroup);
        if (group != null) {
            setHeader(Utils.applyTint(context, context.getDrawable(group.getIconResId()),
                    android.R.attr.colorControlNormal),
                    context.getString(R.string.app_permission_usage_filter_label,
                            group.getLabel()), null, null, true);
            setSummary(context.getString(R.string.app_permission_usage_remove_filter), v -> {
                onPermissionGroupSelected(null);
            });
        }

        // Add the preference header.
        PreferenceCategory category = new PreferenceCategory(context);
        screen.addPreference(category);

        List<Map.Entry<String, Integer>> groupUsagesList = usages.entrySet().stream()
                .sorted(PermissionUsageV2Fragment::comparePermissionGroupUsage)
                .collect(Collectors.toList());

        // If there are no entries, don't show anything.
        if (usages.isEmpty()) {
            screen.removeAll();
        }

        addUIContent(context, groupUsagesList, permApps, category);
    }

    /**
     * Extract the permission usages from mAppPermissionUsages and put the extracted usages
     * into usages and permApps. Returns whether we have seen a system app during the process.
     *
     * TODO: theianchen
     * It's doing two things at the same method which is violating the SOLID principle.
     * We should fix this.
     *
     * @param usages an empty List that will be filled with permission usages.
     * @param permApps an empty List that will be filled with permission apps.
     * @return whether we have seen a system app.
     */
    private boolean extractPermissionUsage(Map<String, Integer> usages,
            ArrayList<PermissionApps.PermissionApp> permApps,
            long startTime) {
        boolean seenSystemApp = false;
        int numApps = mAppPermissionUsages.size();
        for (int appNum = 0; appNum < numApps; appNum++) {
            AppPermissionUsage appUsage = mAppPermissionUsages.get(appNum);
            boolean used = false;
            List<GroupUsage> appGroups = appUsage.getGroupUsages();
            int numGroups = appGroups.size();
            for (int groupNum = 0; groupNum < numGroups; groupNum++) {
                GroupUsage groupUsage = appGroups.get(groupNum);
                String groupName = groupUsage.getGroup().getName();
                long lastAccessTime = groupUsage.getLastAccessTime();

                if (!groupUsage.hasDiscreteData()) {
                    continue;
                }
                if (lastAccessTime == 0) {
                    Log.w(LOG_TAG,
                            "Unexpected access time of 0 for " + appUsage.getApp().getKey() + " "
                                    + groupUsage.getGroup().getName());
                    continue;
                }
                if (lastAccessTime < startTime) {
                    continue;
                }

                final boolean isSystemApp = !Utils.isGroupOrBgGroupUserSensitive(
                        groupUsage.getGroup());
                seenSystemApp = seenSystemApp || isSystemApp;

                used = true;
                addGroupUser(groupName);

                // Filter out usages that aren't of the filtered permission group.
                // We do this after we call addGroupUser so we compute the correct usage counts
                // for the permission filter dialog but before we add the usage to our list.
                if (mFilterGroup != null && !mFilterGroup.equals(groupName)) {
                    continue;
                }

                usages.put(groupName, usages.getOrDefault(groupName, 0) + 1);
            }
            if (used) {
                permApps.add(appUsage.getApp());
                addGroupUser(null);
            }
        }

        return seenSystemApp;
    }

    /**
     * Use the usages and permApps that are previously constructed to add UI content to the page
     */
    private void addUIContent(Context context,
            List<Map.Entry<String, Integer>> usages,
            ArrayList<PermissionApps.PermissionApp> permApps,
            PreferenceCategory category) {
        new PermissionApps.AppDataLoader(context, () -> {
            for (int i = 0; i < usages.size(); i++) {
                Map.Entry<String, Integer> currentEntry = usages.get(i);
                PermissionUsageV2ControlPreference permissionUsagePreference =
                        new PermissionUsageV2ControlPreference(context, currentEntry.getKey(),
                                currentEntry.getValue());
                category.addPreference(permissionUsagePreference);
            }

            setLoading(false, true);
            mFinishedInitialLoad = true;
            setProgressBarVisible(false);
            mPermissionUsages.stopLoader(getActivity().getLoaderManager());
        }).execute(permApps.toArray(new PermissionApps.PermissionApp[0]));
    }

    private void addGroupUser(String app) {
        Integer count = mGroupAppCounts.get(app);
        if (count == null) {
            mGroupAppCounts.put(app, 1);
        } else {
            mGroupAppCounts.put(app, count + 1);
        }
    }

    /**
     * Reloads the data to show.
     */
    private void reloadData() {
        final TimeFilterItem timeFilterItem = mFilterTimes.get(mFilterTimeIndex);
        final long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                - timeFilterItem.getTime(), Instant.EPOCH.toEpochMilli());
        mPermissionUsages.load(null /*filterPackageName*/, null /*filterPermissionGroups*/,
                filterTimeBeginMillis, Long.MAX_VALUE, PermissionUsages.USAGE_FLAG_LAST
                        | PermissionUsages.USAGE_FLAG_HISTORICAL, getActivity().getLoaderManager(),
                false /*getUiInfo*/, false /*getNonPlatformPermissions*/, this /*callback*/,
                false /*sync*/);
        if (mFinishedInitialLoad) {
            setProgressBarVisible(true);
        }
    }

    /**
     * Compare two usages by their access time.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessTime(@NonNull Pair<AppPermissionUsage, GroupUsage> x,
            @NonNull Pair<AppPermissionUsage, GroupUsage> y) {
        return compareAccessTime(x.second, y.second);
    }

    /**
     * Compare two usages by their access time.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x a usage.
     * @param y a usage.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareAccessTime(@NonNull GroupUsage x, @NonNull GroupUsage y) {
        final int timeDiff = compareLong(x.getLastAccessTime(), y.getLastAccessTime());
        if (timeDiff != 0) {
            return timeDiff;
        }
        // Make sure we lose no data if same
        return x.hashCode() - y.hashCode();
    }

    /**
     * Compare two longs. Will order the long values from big to small.
     *
     * Can be used as a {@link java.util.Comparator}.
     *
     * @param x the first long.
     * @param y the second long.
     *
     * @return see {@link java.util.Comparator#compare(Object, Object)}.
     */
    private static int compareLong(long x, long y) {
        if (x > y) {
            return -1;
        } else if (x < y) {
            return 1;
        }
        return 0;
    }

    private static int comparePermissionGroupUsage(@NonNull Map.Entry<String, Integer> x,
            @NonNull Map.Entry<String, Integer> y) {
        int xPermissionOrder = PERMISSION_GROUP_ORDER.getOrDefault(x.getKey(), DEFAULT_ORDER);
        int yPermissionOrder = PERMISSION_GROUP_ORDER.getOrDefault(y.getKey(), DEFAULT_ORDER);
        if (xPermissionOrder != yPermissionOrder) {
            return xPermissionOrder - yPermissionOrder;
        }

        return y.getValue().compareTo(x.getValue());
    }

    /**
     * Get the permission groups declared by the OS.
     *
     * @return a list of the permission groups declared by the OS.
     */
    private @NonNull List<AppPermissionGroup> getOSPermissionGroups() {
        final List<AppPermissionGroup> groups = new ArrayList<>();
        final Set<String> seenGroups = new ArraySet<>();
        final int numGroups = mAppPermissionUsages.size();
        for (int i = 0; i < numGroups; i++) {
            final AppPermissionUsage appUsage = mAppPermissionUsages.get(i);
            final List<GroupUsage> groupUsages = appUsage.getGroupUsages();
            final int groupUsageCount = groupUsages.size();
            for (int j = 0; j < groupUsageCount; j++) {
                final GroupUsage groupUsage = groupUsages.get(j);
                if (Utils.isModernPermissionGroup(groupUsage.getGroup().getName())) {
                    if (seenGroups.add(groupUsage.getGroup().getName())) {
                        groups.add(groupUsage.getGroup());
                    }
                }
            }
        }
        return groups;
    }

    /**
     * Get an AppPermissionGroup that represents the given permission group (and an arbitrary app).
     *
     * @param groupName The name of the permission group.
     *
     * @return an AppPermissionGroup rerepsenting the given permission group or null if no such
     * AppPermissionGroup is found.
     */
    private @Nullable AppPermissionGroup getGroup(@NonNull String groupName) {
        List<AppPermissionGroup> groups = getOSPermissionGroups();
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            if (groups.get(i).getName().equals(groupName)) {
                return groups.get(i);
            }
        }
        return null;
    }

    /**
     * Show a dialog that allows selecting a permission group by which to filter the entries.
     */
    private void showPermissionFilterDialog() {
        Context context = getPreferenceManager().getContext();

        // Get the permission labels.
        List<AppPermissionGroup> groups = getOSPermissionGroups();
        groups.sort(
                (x, y) -> mCollator.compare(x.getLabel().toString(), y.getLabel().toString()));

        // Create the dialog entries.
        String[] groupNames = new String[groups.size() + 1];
        CharSequence[] groupLabels = new CharSequence[groupNames.length];
        int[] groupAccessCounts = new int[groupNames.length];
        groupNames[0] = null;
        groupLabels[0] = context.getString(R.string.permission_usage_any_permission);
        Integer allAccesses = mGroupAppCounts.get(null);
        if (allAccesses == null) {
            allAccesses = 0;
        }
        groupAccessCounts[0] = allAccesses;
        int selection = 0;
        int numGroups = groups.size();
        for (int i = 0; i < numGroups; i++) {
            AppPermissionGroup group = groups.get(i);
            groupNames[i + 1] = group.getName();
            groupLabels[i + 1] = group.getLabel();
            Integer appCount = mGroupAppCounts.get(group.getName());
            if (appCount == null) {
                appCount = 0;
            }
            groupAccessCounts[i + 1] = appCount;
            if (group.getName().equals(mFilterGroup)) {
                selection = i + 1;
            }
        }

        // Create the dialog
        Bundle args = new Bundle();
        args.putCharSequence(PermissionsFilterDialog.TITLE,
                context.getString(R.string.filter_by_title));
        args.putCharSequenceArray(PermissionsFilterDialog.ELEMS,
                groupLabels);
        args.putInt(PermissionsFilterDialog.SELECTION, selection);
        args.putStringArray(PermissionsFilterDialog.GROUPS, groupNames);
        args.putIntArray(PermissionsFilterDialog.ACCESS_COUNTS, groupAccessCounts);
        PermissionsFilterDialog chooserDialog = new PermissionsFilterDialog();
        chooserDialog.setArguments(args);
        chooserDialog.setTargetFragment(this, 0);
        chooserDialog.show(getFragmentManager().beginTransaction(),
                PermissionsFilterDialog.class.getName());
    }

    /**
     * Callback when the user selects a permission group by which to filter.
     *
     * @param selectedGroup The PermissionGroup to use to filter entries, or null if we should show
     *                      all entries.
     */
    private void onPermissionGroupSelected(@Nullable String selectedGroup) {
        Fragment frag = newInstance(selectedGroup, mFilterTimes.get(mFilterTimeIndex).getTime());
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("PermissionUsage")
                .commit();
    }

    /**
     * A dialog that allows the user to select a permission group by which to filter entries.
     *
     * @see #showPermissionFilterDialog()
     */
    public static class PermissionsFilterDialog extends DialogFragment {
        private static final String TITLE = PermissionsFilterDialog.class.getName() + ".arg.title";
        private static final String ELEMS = PermissionsFilterDialog.class.getName() + ".arg.elems";
        private static final String SELECTION = PermissionsFilterDialog.class.getName()
                + ".arg.selection";
        private static final String GROUPS = PermissionsFilterDialog.class.getName()
                + ".arg.groups";
        private static final String ACCESS_COUNTS = PermissionsFilterDialog.class.getName()
                + ".arg.access_counts";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setView(createDialogView());

            return b.create();
        }

        private @NonNull View createDialogView() {
            PermissionUsageV2Fragment fragment = (PermissionUsageV2Fragment) getTargetFragment();
            CharSequence[] elems = getArguments().getCharSequenceArray(ELEMS);
            String[] groups = getArguments().getStringArray(GROUPS);
            int[] accessCounts = getArguments().getIntArray(ACCESS_COUNTS);
            int selectedIndex = getArguments().getInt(SELECTION);

            LayoutInflater layoutInflater = LayoutInflater.from(fragment.getActivity());
            View view = layoutInflater.inflate(R.layout.permission_filter_dialog, null);
            ViewGroup itemsListView = view.requireViewById(R.id.items_container);

            ((TextView) view.requireViewById(R.id.title)).setText(
                    getArguments().getCharSequence(TITLE));

            ActionBarShadowController.attachToView(view.requireViewById(R.id.title_container),
                    getLifecycle(), view.requireViewById(R.id.scroll_view));

            for (int i = 0; i < elems.length; i++) {
                String groupName = groups[i];
                View itemView = layoutInflater.inflate(R.layout.permission_filter_dialog_item,
                        itemsListView, false);

                ((TextView) itemView.requireViewById(R.id.title)).setText(elems[i]);
                ((TextView) itemView.requireViewById(R.id.summary)).setText(
                        getActivity().getResources().getQuantityString(
                                R.plurals.permission_usage_permission_filter_subtitle,
                                accessCounts[i], accessCounts[i]));

                itemView.setOnClickListener((v) -> {
                    dismissAllowingStateLoss();
                    fragment.onPermissionGroupSelected(groupName);
                });

                RadioButton radioButton = itemView.requireViewById(R.id.radio_button);
                radioButton.setChecked(i == selectedIndex);
                radioButton.setOnClickListener((v) -> {
                    dismissAllowingStateLoss();
                    fragment.onPermissionGroupSelected(groupName);
                });

                itemsListView.addView(itemView);
            }

            return view;
        }
    }

    private void showTimeFilterDialog() {
        Context context = getPreferenceManager().getContext();

        CharSequence[] labels = new CharSequence[mFilterTimes.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = mFilterTimes.get(i).getLabel();
        }

        // Create the dialog
        Bundle args = new Bundle();
        args.putCharSequence(TimeFilterDialog.TITLE,
                context.getString(R.string.filter_by_title));
        args.putCharSequenceArray(TimeFilterDialog.ELEMS, labels);
        args.putInt(TimeFilterDialog.SELECTION, mFilterTimeIndex);
        TimeFilterDialog chooserDialog = new TimeFilterDialog();
        chooserDialog.setArguments(args);
        chooserDialog.setTargetFragment(this, 0);
        chooserDialog.show(getFragmentManager().beginTransaction(),
                TimeFilterDialog.class.getName());
    }

    /**
     * Callback when the user selects a time by which to filter.
     *
     * @param selectedIndex The index of the dialog option selected by the user.
     */
    private void onTimeSelected(int selectedIndex) {
        mFilterTimeIndex = selectedIndex;
        reloadData();
    }

    /**
     * A dialog that allows the user to select a time by which to filter entries.
     *
     * @see #showTimeFilterDialog()
     */
    public static class TimeFilterDialog extends DialogFragment {
        private static final String TITLE = TimeFilterDialog.class.getName() + ".arg.title";
        private static final String ELEMS = TimeFilterDialog.class.getName() + ".arg.elems";
        private static final String SELECTION = TimeFilterDialog.class.getName() + ".arg.selection";

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            PermissionUsageV2Fragment fragment = (PermissionUsageV2Fragment) getTargetFragment();
            CharSequence[] elems = getArguments().getCharSequenceArray(ELEMS);
            AlertDialog.Builder b = new AlertDialog.Builder(getActivity())
                    .setTitle(getArguments().getCharSequence(TITLE))
                    .setSingleChoiceItems(elems, getArguments().getInt(SELECTION),
                            (dialog, which) -> {
                                dismissAllowingStateLoss();
                                fragment.onTimeSelected(which);
                            }
                    );

            return b.create();
        }
    }

    /**
     * A class representing a given time, e.g., "in the last hour".
     */
    private static class TimeFilterItem {
        private final long mTime;
        private final @NonNull String mLabel;

        TimeFilterItem(long time, @NonNull String label) {
            mTime = time;
            mLabel = label;
        }

        /**
         * Get the time represented by this object in milliseconds.
         *
         * @return the time represented by this object.
         */
        public long getTime() {
            return mTime;
        }

        public @NonNull String getLabel() {
            return mLabel;
        }
    }
}
