package com.laf.lostandfound.activities;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.firestore.ListenerRegistration;
import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ActivityMainBinding;
import com.laf.lostandfound.fragments.HomeFragment;
import com.laf.lostandfound.fragments.NotificationsFragment;
import com.laf.lostandfound.fragments.ProfileFragment;
import com.laf.lostandfound.fragments.SearchFragment;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.NotificationRepository;
import com.laf.lostandfound.utils.AuthGuard;

public class MainActivity extends AppCompatActivity {

    private static final String STATE_TAB = "state_tab";

    private static final String TAG_HOME = "home";
    private static final String TAG_SEARCH = "search";
    private static final String TAG_NOTIFICATIONS = "notifications";
    private static final String TAG_PROFILE = "profile";

    private ActivityMainBinding binding;

    private Fragment homeFragment;
    private Fragment searchFragment;
    private Fragment notificationsFragment;
    private Fragment profileFragment;
    private Fragment activeFragment;

    private FragmentManager fm;
    private ListenerRegistration unreadRegistration;
    private int selectedTab = R.id.nav_home;

    private final ActivityResultLauncher<String> notificationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                AuthRepository.getInstance().syncFcmToken();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireSignedIn(this)) {
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fm = getSupportFragmentManager();
        setupFragmentManagement(savedInstanceState);
        setupBottomNavigation();
        attachUnreadBadge();
        checkNotificationPermission();

        binding.bottomNavigation.setSelectedItemId(selectedTab);
        switchFragment(fragmentForTab(selectedTab));
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
        }
        AuthRepository.getInstance().syncFcmToken();
    }

    private void setupFragmentManagement(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            searchFragment = new SearchFragment();
            notificationsFragment = new NotificationsFragment();
            profileFragment = new ProfileFragment();

            fm.beginTransaction()
                    .add(R.id.fragmentContainer, profileFragment, TAG_PROFILE)
                    .hide(profileFragment)
                    .add(R.id.fragmentContainer, notificationsFragment, TAG_NOTIFICATIONS)
                    .hide(notificationsFragment)
                    .add(R.id.fragmentContainer, searchFragment, TAG_SEARCH)
                    .hide(searchFragment)
                    .add(R.id.fragmentContainer, homeFragment, TAG_HOME)
                    .commitNow();

            activeFragment = homeFragment;
            selectedTab = R.id.nav_home;
            return;
        }

        homeFragment = fm.findFragmentByTag(TAG_HOME);
        searchFragment = fm.findFragmentByTag(TAG_SEARCH);
        notificationsFragment = fm.findFragmentByTag(TAG_NOTIFICATIONS);
        profileFragment = fm.findFragmentByTag(TAG_PROFILE);

        if (homeFragment == null) {
            homeFragment = new HomeFragment();
            fm.beginTransaction().add(R.id.fragmentContainer, homeFragment, TAG_HOME)
                    .commitNow();
        }
        if (searchFragment == null) {
            searchFragment = new SearchFragment();
            fm.beginTransaction().add(R.id.fragmentContainer, searchFragment, TAG_SEARCH)
                    .hide(searchFragment).commitNow();
        }
        if (notificationsFragment == null) {
            notificationsFragment = new NotificationsFragment();
            fm.beginTransaction()
                    .add(R.id.fragmentContainer, notificationsFragment, TAG_NOTIFICATIONS)
                    .hide(notificationsFragment).commitNow();
        }
        if (profileFragment == null) {
            profileFragment = new ProfileFragment();
            fm.beginTransaction().add(R.id.fragmentContainer, profileFragment, TAG_PROFILE)
                    .hide(profileFragment).commitNow();
        }

        selectedTab = savedInstanceState.getInt(STATE_TAB, R.id.nav_home);
        activeFragment = nonHiddenFragment();
    }

    private void setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                switchFragment(searchFragment);
                return true;
            } else if (itemId == R.id.nav_report) {
                startActivity(new Intent(MainActivity.this, ReportWizardActivity.class));
                return false;
            } else if (itemId == R.id.nav_notifications) {
                switchFragment(notificationsFragment);
                return true;
            } else if (itemId == R.id.nav_profile) {
                switchFragment(profileFragment);
                return true;
            }
            return false;
        });
    }

    /**
     * Switches to the Search tab and runs a query. Used by the home screen search
     * bar so there is a single search implementation.
     */
    public void openSearch(String query) {
        binding.bottomNavigation.setSelectedItemId(R.id.nav_search);
        switchFragment(searchFragment);
        if (searchFragment instanceof SearchFragment) {
            ((SearchFragment) searchFragment).applyQuery(query);
        }
    }

    private void switchFragment(Fragment target) {
        if (target == null) return;
        selectedTab = tabForFragment(target);

        if (target == activeFragment) return;

        FragmentTransaction transaction = fm.beginTransaction();
        for (Fragment fragment : allFragments()) {
            if (fragment != null && fragment != target && !fragment.isHidden()) {
                transaction.hide(fragment);
            }
        }
        if (!target.isAdded()) {
            transaction.add(R.id.fragmentContainer, target, tagFor(target));
        } else if (target.isHidden()) {
            transaction.show(target);
        }
        transaction.commit();
        activeFragment = target;
    }

    private Fragment[] allFragments() {
        return new Fragment[]{homeFragment, searchFragment, notificationsFragment, profileFragment};
    }

    private Fragment fragmentForTab(int tabId) {
        if (tabId == R.id.nav_search) return searchFragment;
        if (tabId == R.id.nav_notifications) return notificationsFragment;
        if (tabId == R.id.nav_profile) return profileFragment;
        return homeFragment;
    }

    private int tabForFragment(Fragment fragment) {
        if (fragment == searchFragment) return R.id.nav_search;
        if (fragment == notificationsFragment) return R.id.nav_notifications;
        if (fragment == profileFragment) return R.id.nav_profile;
        return R.id.nav_home;
    }

    private String tagFor(Fragment fragment) {
        if (fragment == searchFragment) return TAG_SEARCH;
        if (fragment == notificationsFragment) return TAG_NOTIFICATIONS;
        if (fragment == profileFragment) return TAG_PROFILE;
        return TAG_HOME;
    }

    private Fragment nonHiddenFragment() {
        for (Fragment fragment : allFragments()) {
            if (fragment != null && fragment.isAdded() && !fragment.isHidden()) {
                return fragment;
            }
        }
        return homeFragment;
    }

    private void attachUnreadBadge() {
        String uid = AuthRepository.getInstance().getCurrentUserId();
        if (uid.isEmpty()) return;

        unreadRegistration = NotificationRepository.getInstance().listenToUnreadCount(uid,
                new NotificationRepository.CountCallback() {
                    @Override
                    public void onCount(int count) {
                        if (binding == null) return;
                        if (count <= 0) {
                            binding.bottomNavigation.removeBadge(R.id.nav_notifications);
                            return;
                        }
                        BadgeDrawable badge = binding.bottomNavigation
                                .getOrCreateBadge(R.id.nav_notifications);
                        badge.setVisible(true);
                        badge.setMaxCharacterCount(3);
                        badge.setNumber(count);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // The badge is optional; a rules/network error must not break navigation.
                    }
                });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(STATE_TAB, selectedTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (unreadRegistration != null) {
            unreadRegistration.remove();
            unreadRegistration = null;
        }
        super.onDestroy();
        binding = null;
    }
}
