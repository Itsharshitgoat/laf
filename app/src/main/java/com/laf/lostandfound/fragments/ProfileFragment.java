package com.laf.lostandfound.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.laf.lostandfound.R;
import com.laf.lostandfound.activities.ItemDetailActivity;
import com.laf.lostandfound.activities.LoginActivity;
import com.laf.lostandfound.adapters.ActivityAdapter;
import com.laf.lostandfound.adapters.MatchAdapter;
import com.laf.lostandfound.adapters.MyPostAdapter;
import com.laf.lostandfound.databinding.FragmentProfileBinding;
import com.laf.lostandfound.models.ActivityItem;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.models.MatchRecord;
import com.laf.lostandfound.models.User;
import com.laf.lostandfound.repositories.ActivityRepository;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.ItemRepository;
import com.laf.lostandfound.repositories.MatchRepository;
import com.laf.lostandfound.repositories.StorageRepository;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.ImageLoader;
import com.laf.lostandfound.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private AuthRepository authRepository;
    private ItemRepository itemRepository;
    private ActivityRepository activityRepository;
    private StorageRepository storageRepository;
    private MatchRepository matchRepository;

    private MyPostAdapter myPostAdapter;
    private ActivityAdapter activityAdapter;
    private MatchAdapter matchAdapter;
    private final List<Item> myItems = new ArrayList<>();
    private final List<ActivityItem> activities = new ArrayList<>();
    private final List<MatchRecord> userMatches = new ArrayList<>();

    private ListenerRegistration profileRegistration;
    private ListenerRegistration itemsRegistration;
    private ListenerRegistration activityRegistration;
    private ListenerRegistration matchesRegistration;
    private User profileUser;

    private final ActivityResultLauncher<String> avatarPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) uploadAvatar(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        authRepository = AuthRepository.getInstance();
        itemRepository = ItemRepository.getInstance();
        activityRepository = ActivityRepository.getInstance();
        storageRepository = StorageRepository.getInstance();
        matchRepository = MatchRepository.getInstance();

        setupLists();
        setupActions();
        renderFallback();
        attachProfile();
        attachReports();
        attachActivity();
        attachMatches();
    }

    private void setupLists() {
        myPostAdapter = new MyPostAdapter(myItems, (item, position) -> openItem(item));
        binding.rvMyPosts.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvMyPosts.setAdapter(myPostAdapter);

        matchAdapter = new MatchAdapter(new MatchAdapter.MatchActionListener() {
            @Override
            public void onOpen(MatchRecord match) {
                if (match == null || match.getMatchedItemId() == null || match.getMatchedItemId().isEmpty()) return;
                Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
                intent.putExtra(Constants.EXTRA_ITEM_ID, match.getMatchedItemId());
                startActivity(intent);
            }

            @Override
            public void onDismiss(MatchRecord match) {
                if (match != null && match.getId() != null) {
                    matchRepository.dismissMatch(match.getId(), new MatchRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            userMatches.remove(match);
                            matchAdapter.remove(match);
                            updateMatchesVisibility();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            Toast.makeText(requireContext(), "Could not dismiss: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
        binding.rvUserMatches.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUserMatches.setAdapter(matchAdapter);

        activityAdapter = new ActivityAdapter(activities, activity -> {
            if (activity.getItemId() == null || activity.getItemId().isEmpty()) return;
            Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
            intent.putExtra(Constants.EXTRA_ITEM_ID, activity.getItemId());
            startActivity(intent);
        });
        binding.rvActivity.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActivity.setAdapter(activityAdapter);
    }

    private void setupActions() {
        binding.btnChangeAvatar.setOnClickListener(v -> avatarPicker.launch("image/*"));
        binding.btnEditProfile.setOnClickListener(v -> showEditProfileDialog());
        binding.btnSignOut.setOnClickListener(v -> signOut());
    }

    private void renderFallback() {
        FirebaseUser currentUser = authRepository.getCurrentUser();
        if (currentUser == null) {
            binding.tvUserName.setText("Campus User");
            binding.tvUserHandle.setText("@guest");
            return;
        }
        String name = currentUser.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = emailHandle(currentUser.getEmail());
        }
        binding.tvUserName.setText(name);
        binding.tvUserHandle.setText("@" + emailHandle(currentUser.getEmail()));
        ImageLoader.loadAvatar(requireContext(), currentUser.getPhotoUrl() != null
                ? currentUser.getPhotoUrl().toString() : "", binding.ivProfileImage);
        binding.pbProfile.setVisibility(View.VISIBLE);
    }

    private void attachProfile() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) {
            binding.pbProfile.setVisibility(View.GONE);
            return;
        }
        if (profileRegistration != null) profileRegistration.remove();
        profileRegistration = authRepository.listenToUserProfile(uid, new AuthRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                if (binding == null) return;
                binding.pbProfile.setVisibility(View.GONE);
                profileUser = user;
                renderUser(user);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.pbProfile.setVisibility(View.GONE);
                // Firebase Auth still gives us a usable name/photo.
            }
        });
    }

    private void renderUser(User user) {
        if (user == null) return;
        FirebaseUser firebaseUser = authRepository.getCurrentUser();

        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            name = firebaseUser != null ? firebaseUser.getDisplayName() : null;
        }
        if (name == null || name.trim().isEmpty()) {
            name = emailHandle(user.getEmail());
        }
        binding.tvUserName.setText(name);

        String handle = user.getUsername();
        if (handle == null || handle.trim().isEmpty()) {
            handle = emailHandle(user.getEmail());
        }
        binding.tvUserHandle.setText("@" + handle);

        if (user.getBio() != null && !user.getBio().trim().isEmpty()) {
            binding.tvUserBio.setText(user.getBio().trim());
            binding.tvUserBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvUserBio.setVisibility(View.GONE);
        }

        String photo = user.getPhotoUrl();
        if ((photo == null || photo.trim().isEmpty()) && firebaseUser != null
                && firebaseUser.getPhotoUrl() != null) {
            photo = firebaseUser.getPhotoUrl().toString();
        }
        ImageLoader.loadAvatar(requireContext(), photo, binding.ivProfileImage);

        binding.ivVerifiedBadge.setVisibility(
                user.isVerified() ? View.VISIBLE : View.INVISIBLE);
    }

    private void attachReports() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        if (itemsRegistration != null) itemsRegistration.remove();
        itemsRegistration = itemRepository.listenToUserItems(uid,
                new ItemRepository.ItemListCallback() {
                    @Override
                    public void onSuccess(List<Item> items) {
                        if (binding == null) return;
                        myItems.clear();
                        if (items != null) myItems.addAll(items);

                        myPostAdapter.updateItems(myItems);
                        updateStats(myItems);

                        boolean empty = myItems.isEmpty();
                        binding.rvMyPosts.setVisibility(empty ? View.GONE : View.VISIBLE);
                        binding.tvMyPostsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        binding.tvMyPostsCount.setText(empty ? "" : myItems.size() + " total");
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.tvMyPostsEmpty.setVisibility(View.VISIBLE);
                        binding.tvMyPostsEmpty.setText("Could not load your reports. Pull to refresh later.");
                    }
                });
    }

    private void attachActivity() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        if (activityRegistration != null) activityRegistration.remove();
        activityRegistration = activityRepository.listenToUserActivities(uid,
                new ActivityRepository.ActivityListCallback() {
                    @Override
                    public void onSuccess(List<ActivityItem> list) {
                        if (binding == null) return;
                        activities.clear();
                        if (list != null) activities.addAll(list);
                        activityAdapter.updateItems(activities);

                        boolean empty = activities.isEmpty();
                        binding.rvActivity.setVisibility(empty ? View.GONE : View.VISIBLE);
                        binding.tvActivityEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.tvActivityEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void updateStats(List<Item> items) {
        int reported = items.size();
        int found = 0;
        int returned = 0;
        int active = 0;

        for (Item item : items) {
            if (Constants.TYPE_FOUND.equalsIgnoreCase(item.getType())) found++;
            if (Constants.isReturnedStatus(item.getStatus())) {
                returned++;
            } else if (Constants.isActiveStatus(item.getStatus())) {
                active++;
            }
        }

        binding.tvStatReported.setText(String.valueOf(reported));
        binding.tvStatFound.setText(String.valueOf(found));
        binding.tvStatReturned.setText(String.valueOf(returned));
        binding.tvStatActive.setText(String.valueOf(active));
    }

    private void showEditProfileDialog() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        String currentName = profileUser != null ? profileUser.getDisplayName() : null;
        String currentBio = profileUser != null ? profileUser.getBio() : null;
        if ((currentName == null || currentName.trim().isEmpty()) && firebaseUser != null) {
            currentName = firebaseUser.getDisplayName();
        }
        if (currentName == null) currentName = "";

        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);

        EditText nameInput = new EditText(requireContext());
        nameInput.setHint("Display name");
        nameInput.setSingleLine(true);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setText(currentName);
        container.addView(nameInput);

        EditText usernameInput = new EditText(requireContext());
        usernameInput.setHint("Username (handle)");
        usernameInput.setSingleLine(true);
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        usernameInput.setText(profileUser != null && profileUser.getUsername() != null ? profileUser.getUsername() : "");
        container.addView(usernameInput);

        EditText phoneInput = new EditText(requireContext());
        phoneInput.setHint("Phone number (shared only on approved claims)");
        phoneInput.setSingleLine(true);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneInput.setText(profileUser != null && profileUser.getPhone() != null ? profileUser.getPhone() : "");
        container.addView(phoneInput);

        EditText bioInput = new EditText(requireContext());
        bioInput.setHint("Short bio (optional)");
        bioInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        bioInput.setMinLines(2);
        bioInput.setMaxLines(4);
        bioInput.setText(currentBio == null ? "" : currentBio);
        container.addView(bioInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Edit profile")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String username = usernameInput.getText().toString().trim();
                    String phone = phoneInput.getText().toString().trim();
                    String bio = bioInput.getText().toString().trim();
                    if (!ValidationUtils.isValidName(name)) {
                        Toast.makeText(requireContext(), "Please enter a name with at least 2 characters",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!phone.isEmpty() && !ValidationUtils.isValidPhone(phone)) {
                        Toast.makeText(requireContext(), "Please enter a valid phone number",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveProfile(uid, name, bio, phone, username);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveProfile(String uid, String name, String bio, String phone, String username) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("displayName", name);
        updates.put("bio", bio);
        updates.put("phone", phone);
        if (!username.isEmpty()) {
            updates.put("username", username.startsWith("@") ? username.substring(1) : username);
        }

        binding.pbProfile.setVisibility(View.VISIBLE);
        authRepository.updateUserFields(uid, updates, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess() {
                if (binding == null) return;
                binding.pbProfile.setVisibility(View.GONE);
                binding.tvUserName.setText(name);
                if (!username.isEmpty()) {
                    binding.tvUserHandle.setText("@" + (username.startsWith("@") ? username.substring(1) : username));
                }
                if (bio.isEmpty()) {
                    binding.tvUserBio.setVisibility(View.GONE);
                } else {
                    binding.tvUserBio.setText(bio);
                    binding.tvUserBio.setVisibility(View.VISIBLE);
                }
                Toast.makeText(requireContext(), "Profile updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.pbProfile.setVisibility(View.GONE);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void uploadAvatar(Uri uri) {
        if (binding == null) return;
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        String previousPath = profileUser != null ? profileUser.getAvatarStoragePath() : null;

        binding.pbProfile.setVisibility(View.VISIBLE);
        binding.btnChangeAvatar.setEnabled(false);
        storageRepository.uploadAvatar(uri, new StorageRepository.UploadCallback() {
            @Override
            public void onSuccess(StorageRepository.UploadResult result) {
                if (binding == null) return;
                Map<String, Object> updates = new HashMap<>();
                updates.put("photoUrl", result.getDownloadUrl());
                updates.put("avatarStoragePath", result.getStoragePath());

                authRepository.updateUserFields(uid, updates, new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding == null) return;
                        binding.pbProfile.setVisibility(View.GONE);
                        binding.btnChangeAvatar.setEnabled(true);
                        ImageLoader.loadAvatar(requireContext(), result.getDownloadUrl(),
                                binding.ivProfileImage);
                        if (previousPath != null && !previousPath.trim().isEmpty()
                                && !previousPath.equals(result.getStoragePath())) {
                            storageRepository.deleteByPath(previousPath, null);
                        }
                        Toast.makeText(requireContext(), "Profile photo updated",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.pbProfile.setVisibility(View.GONE);
                        binding.btnChangeAvatar.setEnabled(true);
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.pbProfile.setVisibility(View.GONE);
                binding.btnChangeAvatar.setEnabled(true);
                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void attachMatches() {
        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) return;

        if (matchesRegistration != null) matchesRegistration.remove();
        matchesRegistration = matchRepository.listenToUserMatches(uid, new MatchRepository.MatchListCallback() {
            @Override
            public void onSuccess(List<MatchRecord> matches) {
                if (binding == null) return;
                userMatches.clear();
                if (matches != null) userMatches.addAll(matches);
                matchAdapter.updateMatches(userMatches);
                updateMatchesVisibility();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                binding.layoutMatchesHeader.setVisibility(View.GONE);
                binding.rvUserMatches.setVisibility(View.GONE);
            }
        });
    }

    private void updateMatchesVisibility() {
        if (binding == null) return;
        boolean hasMatches = !userMatches.isEmpty();
        binding.layoutMatchesHeader.setVisibility(hasMatches ? View.VISIBLE : View.GONE);
        binding.rvUserMatches.setVisibility(hasMatches ? View.VISIBLE : View.GONE);
        if (hasMatches) {
            binding.tvMatchesCount.setText(userMatches.size() + " suggested");
        }
    }

    private void openItem(Item item) {
        if (item == null) return;
        String itemId = item.getItemId() != null ? item.getItemId() : item.getId();
        if (itemId == null || itemId.isEmpty()) return;
        Intent intent = new Intent(requireContext(), ItemDetailActivity.class);
        intent.putExtra(Constants.EXTRA_ITEM_ID, itemId);
        startActivity(intent);
    }

    private void signOut() {
        authRepository.signOut();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    private static String emailHandle(String email) {
        if (email == null || email.trim().isEmpty()) return "campususer";
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (profileRegistration != null) {
            profileRegistration.remove();
            profileRegistration = null;
        }
        if (itemsRegistration != null) {
            itemsRegistration.remove();
            itemsRegistration = null;
        }
        if (activityRegistration != null) {
            activityRegistration.remove();
            activityRegistration = null;
        }
        if (matchesRegistration != null) {
            matchesRegistration.remove();
            matchesRegistration = null;
        }
        binding = null;
    }
}
