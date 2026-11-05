package com.laf.lostandfound.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;
import com.laf.lostandfound.R;
import com.laf.lostandfound.adapters.ClaimAdapter;
import com.laf.lostandfound.adapters.MatchAdapter;
import com.laf.lostandfound.databinding.ActivityItemDetailBinding;
import com.laf.lostandfound.databinding.DialogClaimBinding;
import com.laf.lostandfound.databinding.DialogClaimsBinding;
import com.laf.lostandfound.databinding.DialogEditItemBinding;
import com.laf.lostandfound.databinding.DialogMatchesBinding;
import com.laf.lostandfound.databinding.DialogOwnerContactBinding;
import com.laf.lostandfound.models.ActivityItem;
import com.laf.lostandfound.models.Claim;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.models.MatchRecord;
import com.laf.lostandfound.models.Notification;
import com.laf.lostandfound.models.ReportAbuse;
import com.laf.lostandfound.models.User;
import com.laf.lostandfound.repositories.ActivityRepository;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.ClaimRepository;
import com.laf.lostandfound.repositories.ItemRepository;
import com.laf.lostandfound.repositories.MatchRepository;
import com.laf.lostandfound.repositories.NotificationRepository;
import com.laf.lostandfound.repositories.ReportRepository;
import com.laf.lostandfound.repositories.StorageRepository;
import com.laf.lostandfound.utils.AuthGuard;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.ImageLoader;
import com.laf.lostandfound.utils.TimeUtils;
import com.laf.lostandfound.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Item detail screen.
 *
 * <p>Visitors never see the reporter's phone number. Instead they send a
 * contact request (a claim); the owner reviews requests and, when approving
 * one, explicitly shares contact details. The chosen claimant then sees the
 * owner's contact on this screen.</p>
 */
public class ItemDetailActivity extends AppCompatActivity {

    private ActivityItemDetailBinding binding;

    private ItemRepository itemRepository;
    private AuthRepository authRepository;
    private ClaimRepository claimRepository;
    private NotificationRepository notificationRepository;
    private StorageRepository storageRepository;
    private ActivityRepository activityRepository;
    private MatchRepository matchRepository;

    private ListenerRegistration itemRegistration;
    private ListenerRegistration ownerClaimsRegistration;
    private ListenerRegistration myClaimRegistration;
    private ListenerRegistration matchesRegistration;

    private DialogClaimsBinding claimsDialogBinding;
    private ClaimAdapter claimsAdapter;
    private AlertDialog claimsDialog;

    private DialogMatchesBinding matchesDialogBinding;
    private MatchAdapter matchesAdapter;
    private AlertDialog matchesDialog;

    private final List<Claim> ownerClaims = new ArrayList<>();
    private final List<MatchRecord> possibleMatches = new ArrayList<>();

    private Item currentItem;
    private Claim myClaim;
    private String itemId;
    private boolean isOwner;
    private boolean ownerClaimsAttached;
    private boolean myClaimAttached;
    private boolean deleting;
    private boolean submittingClaim;

    private String myName = "Campus user";
    private String myContact = "";
    private String defaultOwnerContact = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireSignedIn(this)) {
            return;
        }

        binding = ActivityItemDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        itemRepository = ItemRepository.getInstance();
        authRepository = AuthRepository.getInstance();
        claimRepository = ClaimRepository.getInstance();
        notificationRepository = NotificationRepository.getInstance();
        storageRepository = StorageRepository.getInstance();
        activityRepository = ActivityRepository.getInstance();
        matchRepository = MatchRepository.getInstance();

        itemId = getIntent().getStringExtra(Constants.EXTRA_ITEM_ID);
        if (itemId == null || itemId.trim().isEmpty()) {
            Uri data = getIntent().getData();
            if (data != null && "laf".equalsIgnoreCase(data.getScheme())) {
                String lastSegment = data.getLastPathSegment();
                if (lastSegment != null && !lastSegment.trim().isEmpty()) {
                    itemId = lastSegment.trim();
                } else if (data.getHost() != null && !"item".equalsIgnoreCase(data.getHost())) {
                    itemId = data.getHost().trim();
                }
            }
        }
        if (itemId == null || itemId.trim().isEmpty()) {
            Toast.makeText(this, "Item not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        itemId = itemId.trim();

        setupToolbar();
        loadMyProfile();
        attachItemListener();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
    }

    private void loadMyProfile() {
        FirebaseUser firebaseUser = authRepository.getCurrentUser();
        if (firebaseUser != null) {
            myName = fallbackName(firebaseUser);
            if (firebaseUser.getEmail() != null) {
                myContact = firebaseUser.getEmail().trim();
                defaultOwnerContact = myContact;
            }
        }

        authRepository.getUserProfile(authRepository.getCurrentUserId(),
                new AuthRepository.UserCallback() {
                    @Override
                    public void onSuccess(User user) {
                        if (user == null) return;
                        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
                            myName = user.getDisplayName().trim();
                        }
                        if (user.getPhone() != null && !user.getPhone().trim().isEmpty()) {
                            myContact = user.getPhone().trim();
                            defaultOwnerContact = myContact;
                        } else if (user.getEmail() != null && !user.getEmail().trim().isEmpty()) {
                            myContact = user.getEmail().trim();
                            defaultOwnerContact = myContact;
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        // The FirebaseUser fallback above is enough to prefill the forms.
                    }
                });
    }

    // ---------------------------------------------------------------------
    // Realtime item data
    // ---------------------------------------------------------------------

    private void attachItemListener() {
        binding.progressBar.setVisibility(View.VISIBLE);
        itemRegistration = itemRepository.getItemRealtime(itemId,
                new ItemRepository.ItemDataCallback() {
                    @Override
                    public void onSuccess(Item item) {
                        if (binding == null || item == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        currentItem = item;
                        isOwner = authRepository.getCurrentUserId().equals(item.getUserId());

                        populateViews(item);
                        renderActions(item);

                        if (!isOwner && !myClaimAttached) {
                            attachMyClaimListener();
                        }
                        if (isOwner && !ownerClaimsAttached) {
                            attachOwnerClaims();
                        }
                        if (isOwner && matchesRegistration == null) {
                            attachMatches();
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.progressBar.setVisibility(View.GONE);
                        String message = errorMessage == null
                                ? "Unable to load this report" : errorMessage;
                        boolean removed = message.toLowerCase().contains("no longer available")
                                || message.toLowerCase().contains("not found");
                        Toast.makeText(ItemDetailActivity.this, message, Toast.LENGTH_LONG).show();
                        // The item was deleted/removed after we opened it: leaving a
                        // stale detail screen up would let the user act on a dead doc.
                        if (currentItem == null || removed) finish();
                    }
                });
    }

    private void populateViews(Item item) {
        ImageLoader.loadImage(this, item.getImageUrl(), binding.ivItemImage, 24);

        boolean isFound = Constants.TYPE_FOUND.equalsIgnoreCase(item.getType());
        binding.tvBadge.setText(isFound ? "FOUND" : "LOST");
        binding.tvBadge.setBackgroundResource(
                isFound ? R.drawable.bg_badge_found : R.drawable.bg_badge_lost);

        bindStatus(item.getStatus());

        binding.tvTitle.setText(emptyTo(item.getTitle(), "Untitled report"));
        binding.tvTimeAgo.setText(TimeUtils.getIncidentLabel(item.getType(), item.getIncidentAt()));
        binding.tvDescription.setText(emptyTo(item.getDescription(),
                "No description was provided for this report."));

        bindChip(binding.tvCategory, item.getCategory(), "");
        bindChip(binding.tvColor, item.getPrimaryColor(), "Color: ");
        bindAiDetails(item);

        String location = item.getLocation();
        boolean hasLocation = location != null && !location.trim().isEmpty();
        boolean hasCoordinates = item.getLatitude() != 0 || item.getLongitude() != 0;
        binding.tvLocation.setText(hasLocation ? location.trim() : "Location not specified");
        binding.btnGetDirections.setVisibility(hasLocation || hasCoordinates
                ? View.VISIBLE : View.GONE);
        binding.btnGetDirections.setOnClickListener(v -> openDirections(item));

        binding.tvUserName.setText(emptyTo(item.getUserName(), "Campus user"));
        ImageLoader.loadAvatar(this, item.getUserPhotoUrl(), binding.ivUserAvatar);
        binding.ivVerifiedBadge.setVisibility(item.isUserVerified() ? View.VISIBLE : View.GONE);
        binding.tvUserMeta.setText(item.isUserVerified()
                ? "Verified member" : "Campus member");
    }

    private void bindStatus(String status) {
        String label = "Active";
        int color = R.color.primary;
        if (Constants.STATUS_CLAIMED.equalsIgnoreCase(status)) {
            label = "Claimed";
            color = R.color.primary_light;
        } else if (Constants.STATUS_RETURNED.equalsIgnoreCase(status)
                || Constants.STATUS_RESOLVED.equalsIgnoreCase(status)) {
            label = "Returned";
            color = R.color.success;
        } else if (Constants.STATUS_CLOSED.equalsIgnoreCase(status)) {
            label = "Closed";
            color = R.color.grey_600;
        }

        binding.tvItemStatus.setText(label);
        ViewCompat.setBackgroundTintList(binding.tvItemStatus,
                ColorStateList.valueOf(ContextCompat.getColor(this, color)));
    }

    private void bindChip(TextView view, String value, String prefix) {
        if (value == null || value.trim().isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(prefix + value.trim());
        view.setVisibility(View.VISIBLE);
    }

    private void bindAiDetails(Item item) {
        List<String> features = item.getFeatures();
        boolean hasBrand = notEmpty(item.getBrand());
        boolean hasMaterial = notEmpty(item.getMaterial());
        boolean hasSecondary = item.getSecondaryColors() != null
                && !item.getSecondaryColors().isEmpty();
        boolean hasFeatures = features != null && !features.isEmpty();
        boolean hasAi = hasBrand || hasMaterial || hasSecondary || hasFeatures
                || notEmpty(item.getAiStatus());

        binding.cardAiDetails.setVisibility(hasAi ? View.VISIBLE : View.GONE);
        if (!hasAi) return;

        String summary = "";
        if ("completed".equalsIgnoreCase(item.getAiStatus())) {
            summary = item.getAiConfidence() > 0
                    ? "AI confidence " + Math.round(item.getAiConfidence()) + "%"
                    : "Photo tagged automatically";
            if (notEmpty(item.getAiModel())) {
                summary += " · " + item.getAiModel().trim();
            }
        } else if ("failed".equalsIgnoreCase(item.getAiStatus())) {
            summary = "AI analysis was unavailable — the details were entered manually.";
        } else if ("manual".equalsIgnoreCase(item.getAiStatus())) {
            summary = "Details were entered manually.";
        }
        binding.tvAiSummary.setText(summary);
        binding.tvAiSummary.setVisibility(notEmpty(summary) ? View.VISIBLE : View.GONE);

        binding.tvBrand.setText("Brand: " + safeTrim(item.getBrand()));
        binding.tvBrand.setVisibility(hasBrand ? View.VISIBLE : View.GONE);

        binding.tvMaterial.setText("Material: " + safeTrim(item.getMaterial()));
        binding.tvMaterial.setVisibility(hasMaterial ? View.VISIBLE : View.GONE);

        binding.tvSecondaryColors.setText("Other colors: " + join(item.getSecondaryColors()));
        binding.tvSecondaryColors.setVisibility(hasSecondary ? View.VISIBLE : View.GONE);

        bindFeatureChips(features);
    }

    private void bindFeatureChips(List<String> features) {
        binding.chipGroupFeatures.removeAllViews();
        if (features == null || features.isEmpty()) {
            binding.chipGroupFeatures.setVisibility(View.GONE);
            return;
        }

        int added = 0;
        for (String feature : features) {
            if (feature == null || feature.trim().isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText(feature.trim());
            chip.setCheckable(false);
            chip.setClickable(false);
            chip.setChipBackgroundColorResource(R.color.chip_unselected_bg);
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            chip.setChipStrokeWidth(0f);
            chip.setEnsureMinTouchTargetSize(false);
            binding.chipGroupFeatures.addView(chip);
            added++;
            if (added >= 10) break;
        }

        binding.chipGroupFeatures.setVisibility(
                binding.chipGroupFeatures.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void openDirections(Item item) {
        if (item == null) return;

        try {
            Intent intent;
            if (item.getLatitude() != 0 || item.getLongitude() != 0) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                        "google.navigation:q=" + item.getLatitude() + "," + item.getLongitude()));
                intent.setPackage("com.google.android.apps.maps");
                if (intent.resolveActivity(getPackageManager()) == null) {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.google.com/maps/dir/?api=1&destination="
                                    + item.getLatitude() + "," + item.getLongitude()));
                }
            } else if (notEmpty(item.getLocation())) {
                String encoded = Uri.encode(item.getLocation().trim());
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + encoded));
                intent.setPackage("com.google.android.apps.maps");
                if (intent.resolveActivity(getPackageManager()) == null) {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "https://www.google.com/maps/search/?api=1&query=" + encoded));
                }
            } else {
                Toast.makeText(this, "Location details are not available",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No app is available to open maps",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------------
    // Actions
    // ---------------------------------------------------------------------

    private void renderActions(Item item) {
        if (isOwner) {
            binding.layoutOwnerActions.setVisibility(View.VISIBLE);
            binding.layoutVisitorActions.setVisibility(View.GONE);
            binding.layoutClaimStatus.setVisibility(View.GONE);
            binding.layoutApprovedContact.setVisibility(View.GONE);
            updateOwnerButtons(item);
            setupOwnerActions();
        } else {
            binding.layoutOwnerActions.setVisibility(View.GONE);
            binding.layoutVisitorActions.setVisibility(View.VISIBLE);
            binding.btnClaimItem.setOnClickListener(v -> showClaimDialog());
            renderVisitorState();
        }
    }

    private void updateOwnerButtons(Item item) {
        if (item == null) return;

        int pending = 0;
        for (Claim claim : ownerClaims) {
            if (Constants.CLAIM_PENDING.equalsIgnoreCase(claim.getStatus())) pending++;
        }
        binding.btnViewClaims.setText(pending > 0
                ? "Contact requests (" + pending + ")" : "Contact requests");

        String status = item.getStatus();
        boolean returned = Constants.STATUS_RETURNED.equalsIgnoreCase(status)
                || Constants.STATUS_RESOLVED.equalsIgnoreCase(status);
        boolean closed = Constants.STATUS_CLOSED.equalsIgnoreCase(status);

        binding.btnMarkReturned.setEnabled(!returned && !closed);
        binding.btnMarkReturned.setText(returned ? "Returned ✓" : "Mark as Returned");
        binding.btnCloseReport.setEnabled(!closed);
        binding.btnCloseReport.setText(closed ? "Report closed" : "Close report");
        binding.btnEditReport.setEnabled(!closed);
    }

    private void setupOwnerActions() {
        binding.btnViewMatches.setOnClickListener(v -> showMatchesDialog());
        binding.btnViewClaims.setOnClickListener(v -> showClaimsDialog());
        binding.btnEditReport.setOnClickListener(v -> showEditDialog());
        binding.btnMarkReturned.setOnClickListener(v -> confirmMarkReturned());
        binding.btnCloseReport.setOnClickListener(v -> confirmCloseReport());
        binding.btnDeleteReport.setOnClickListener(v -> confirmDeleteReport());
    }

    private void renderVisitorState() {
        if (currentItem == null) return;

        String itemStatus = currentItem.getStatus();
        boolean returned = Constants.STATUS_RETURNED.equalsIgnoreCase(itemStatus)
                || Constants.STATUS_RESOLVED.equalsIgnoreCase(itemStatus);
        boolean closed = Constants.STATUS_CLOSED.equalsIgnoreCase(itemStatus);
        boolean claimedBySomeone = Constants.STATUS_CLAIMED.equalsIgnoreCase(itemStatus);
        // Legacy documents without an owner id cannot satisfy the claim rules.
        boolean ownerMissing = safeTrim(currentItem.getUserId()).isEmpty();
        boolean unavailable = returned || closed || ownerMissing;

        String claimStatus = myClaim == null ? "" : safeTrim(myClaim.getStatus());
        boolean pending = Constants.CLAIM_PENDING.equalsIgnoreCase(claimStatus);
        boolean approved = Constants.CLAIM_APPROVED.equalsIgnoreCase(claimStatus);
        boolean rejected = Constants.CLAIM_REJECTED.equalsIgnoreCase(claimStatus);

        if (myClaim == null) {
            binding.layoutClaimStatus.setVisibility(View.GONE);
            binding.layoutApprovedContact.setVisibility(View.GONE);
            binding.btnClaimItem.setVisibility(View.VISIBLE);
            binding.btnClaimItem.setEnabled(!unavailable && !claimedBySomeone);
            if (unavailable) {
                binding.btnClaimItem.setText("This report is no longer active");
            } else if (claimedBySomeone) {
                binding.btnClaimItem.setText("This item has already been claimed");
            } else {
                binding.btnClaimItem.setText(R.string.this_is_my_item);
            }
            return;
        }

        if (pending) {
            showClaimStatusCard("Request pending",
                    "The owner has been notified. Their contact details will appear here "
                            + "if they approve your request.");
            binding.layoutApprovedContact.setVisibility(View.GONE);
            binding.btnClaimItem.setVisibility(View.VISIBLE);
            binding.btnClaimItem.setEnabled(false);
            binding.btnClaimItem.setText("Request pending");
            return;
        }

        if (approved) {
            showClaimStatusCard("Request approved",
                    "The owner approved your request. Please arrange the handover using "
                            + "the contact details below.");
            binding.btnClaimItem.setVisibility(View.VISIBLE);
            binding.btnClaimItem.setEnabled(false);
            binding.btnClaimItem.setText("Approved ✓");

            String contact = safeTrim(myClaim.getOwnerContact());
            if (!contact.isEmpty()) {
                binding.tvOwnerContact.setText(contact);
                binding.layoutApprovedContact.setVisibility(View.VISIBLE);
                binding.btnCopyContact.setOnClickListener(v -> copyToClipboard("Owner contact", contact));
            } else {
                binding.layoutApprovedContact.setVisibility(View.GONE);
            }
            return;
        }

        if (rejected) {
            showClaimStatusCard("Request declined",
                    "The owner did not approve this request. If you think this was a "
                            + "mistake, you can send another request with more details.");
            binding.layoutApprovedContact.setVisibility(View.GONE);
            binding.btnClaimItem.setVisibility(View.VISIBLE);
            binding.btnClaimItem.setEnabled(!unavailable && !claimedBySomeone);
            binding.btnClaimItem.setText(unavailable || claimedBySomeone
                    ? "This report is no longer active" : "Send another request");
            return;
        }

        binding.layoutClaimStatus.setVisibility(View.GONE);
        binding.layoutApprovedContact.setVisibility(View.GONE);
        binding.btnClaimItem.setVisibility(View.VISIBLE);
        binding.btnClaimItem.setEnabled(!unavailable && !claimedBySomeone);
        binding.btnClaimItem.setText(R.string.this_is_my_item);
    }

    private void showClaimStatusCard(String title, String message) {
        binding.tvClaimStatusTitle.setText(title);
        binding.tvClaimStatusMessage.setText(message);
        binding.layoutClaimStatus.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------
    // Visitor claim flow
    // ---------------------------------------------------------------------

    private void attachMyClaimListener() {
        myClaimAttached = true;
        String uid = authRepository.getCurrentUserId();
        myClaimRegistration = claimRepository.listenToClaim(itemId, uid,
                new ClaimRepository.ClaimCallback() {
                    @Override
                    public void onSuccess(Claim claim) {
                        if (binding == null) return;
                        myClaim = claim;
                        renderVisitorState();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        myClaim = null;
                        renderVisitorState();
                    }
                });
    }

    private void reattachMyClaimListener() {
        if (myClaimRegistration != null) {
            myClaimRegistration.remove();
            myClaimRegistration = null;
        }
        myClaimAttached = false;
        attachMyClaimListener();
    }

    private void showClaimDialog() {
        if (currentItem == null) return;

        DialogClaimBinding dialogBinding = DialogClaimBinding.inflate(getLayoutInflater());
        dialogBinding.etClaimContact.setText(myContact);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Request contact")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Send request", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String contact = text(dialogBinding.etClaimContact);
                    if (contact.isEmpty()) {
                        dialogBinding.tilClaimContact.setError(
                                "Add a phone number or email so the owner can reach you");
                        return;
                    }
                    dialogBinding.tilClaimContact.setError(null);
                    dialog.dismiss();
                    submitClaim(contact, text(dialogBinding.etClaimMessage));
                }));

        dialog.show();
    }

    private void submitClaim(String contact, String message) {
        if (currentItem == null || submittingClaim) return;
        if (safeTrim(currentItem.getUserId()).isEmpty()) {
            Toast.makeText(this, "This report cannot receive contact requests",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String uid = authRepository.getCurrentUserId();
        if (uid.isEmpty()) {
            AuthGuard.requireSignedIn(this);
            return;
        }

        submittingClaim = true;
        binding.btnClaimItem.setEnabled(false);

        Claim claim = new Claim();
        claim.setItemId(itemId);
        claim.setItemTitle(currentItem.getTitle());
        claim.setOwnerId(currentItem.getUserId());
        claim.setClaimantId(uid);
        claim.setClaimantName(myName);
        claim.setClaimantContact(contact);
        claim.setMessage(message);
        claim.setStatus(Constants.CLAIM_PENDING);

        claimRepository.createClaim(claim, new ClaimRepository.ClaimCallback() {
            @Override
            public void onSuccess(Claim saved) {
                submittingClaim = false;
                if (binding == null) return;
                myClaim = saved;
                renderVisitorState();
                reattachMyClaimListener();
                notifyOwnerOfClaim(saved);
                Toast.makeText(ItemDetailActivity.this,
                        "Contact request sent to the owner", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                submittingClaim = false;
                if (binding == null) return;
                renderVisitorState();
                Toast.makeText(ItemDetailActivity.this,
                        errorMessage == null ? "Unable to send the request" : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void notifyOwnerOfClaim(Claim claim) {
        if (currentItem == null || claim == null) return;
        String ownerId = currentItem.getUserId();
        if (ownerId == null || ownerId.trim().isEmpty()) return;

        notificationRepository.createNotification(new Notification(
                ownerId,
                authRepository.getCurrentUserId(),
                "New contact request",
                myName + " says they own \"" + currentItem.getTitle() + "\".",
                itemId,
                "claim"), null);
    }

    // ---------------------------------------------------------------------
    // Owner: contact requests
    // ---------------------------------------------------------------------

    private void attachOwnerClaims() {
        ownerClaimsAttached = true;
        ownerClaimsRegistration = claimRepository.listenToItemClaims(
                itemId, authRepository.getCurrentUserId(),
                new ClaimRepository.ClaimListCallback() {
                    @Override
                    public void onSuccess(List<Claim> claims) {
                        ownerClaims.clear();
                        if (claims != null) ownerClaims.addAll(claims);
                        if (binding == null) return;

                        updateOwnerButtons(currentItem);
                        if (claimsAdapter != null) {
                            claimsAdapter.updateClaims(ownerClaims);
                        }
                        if (claimsDialogBinding != null) {
                            updateClaimsDialogViews();
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.btnViewClaims.setText("Contact requests");
                    }
                });
    }

    private void attachMatches() {
        matchesRegistration = matchRepository.listenToItemMatches(itemId,
                new MatchRepository.MatchListCallback() {
                    @Override
                    public void onSuccess(List<MatchRecord> matches) {
                        if (binding == null) return;
                        possibleMatches.clear();
                        if (matches != null) possibleMatches.addAll(matches);
                        updateMatchesButton();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (binding == null) return;
                        binding.btnViewMatches.setVisibility(View.GONE);
                    }
                });
    }

    private void updateMatchesButton() {
        if (binding == null) return;
        if (possibleMatches.isEmpty()) {
            binding.btnViewMatches.setVisibility(View.GONE);
            return;
        }
        binding.btnViewMatches.setVisibility(View.VISIBLE);
        binding.btnViewMatches.setText("Possible matches (" + possibleMatches.size() + ")");
    }

    private void showMatchesDialog() {
        if (possibleMatches.isEmpty()) {
            Toast.makeText(this, "No possible matches yet", Toast.LENGTH_SHORT).show();
            return;
        }

        matchesDialogBinding = DialogMatchesBinding.inflate(getLayoutInflater());
        matchesAdapter = new MatchAdapter(new MatchAdapter.MatchActionListener() {
            @Override
            public void onOpen(MatchRecord match) {
                if (matchesDialog != null) matchesDialog.dismiss();
                if (match.getMatchedItemId() == null || match.getMatchedItemId().isEmpty()) return;
                Intent intent = new Intent(ItemDetailActivity.this, ItemDetailActivity.class);
                intent.putExtra(Constants.EXTRA_ITEM_ID, match.getMatchedItemId());
                startActivity(intent);
            }

            @Override
            public void onDismiss(MatchRecord match) {
                if (match.getId() != null) {
                    matchRepository.dismissMatch(match.getId(), new MatchRepository.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            possibleMatches.remove(match);
                            if (matchesAdapter != null) matchesAdapter.remove(match);
                            updateMatchesButton();
                            if (matchesDialogBinding != null && (matchesAdapter == null || matchesAdapter.size() == 0)) {
                                matchesDialogBinding.tvMatchesEmpty.setVisibility(View.VISIBLE);
                                matchesDialogBinding.rvMatches.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            Toast.makeText(ItemDetailActivity.this, "Could not dismiss: " + errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });

        matchesDialogBinding.rvMatches.setLayoutManager(new LinearLayoutManager(this));
        matchesDialogBinding.rvMatches.setAdapter(matchesAdapter);
        matchesAdapter.updateMatches(possibleMatches);
        matchesDialogBinding.tvMatchesEmpty.setVisibility(possibleMatches.isEmpty() ? View.VISIBLE : View.GONE);
        matchesDialogBinding.rvMatches.setVisibility(possibleMatches.isEmpty() ? View.GONE : View.VISIBLE);

        matchesDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Possible matches")
                .setView(matchesDialogBinding.getRoot())
                .setNegativeButton("Close", null)
                .create();
        matchesDialog.setOnDismissListener(d -> {
            matchesDialog = null;
            matchesDialogBinding = null;
            matchesAdapter = null;
        });
        matchesDialog.show();
    }

    private void showClaimsDialog() {
        if (currentItem == null) return;

        claimsDialogBinding = DialogClaimsBinding.inflate(getLayoutInflater());
        claimsAdapter = new ClaimAdapter(new ClaimAdapter.ClaimActionListener() {
            @Override
            public void onApprove(Claim claim) {
                showOwnerContactDialog(claim);
            }

            @Override
            public void onReject(Claim claim) {
                confirmRejectClaim(claim);
            }
        });
        claimsDialogBinding.rvClaims.setLayoutManager(new LinearLayoutManager(this));
        claimsDialogBinding.rvClaims.setAdapter(claimsAdapter);
        claimsAdapter.updateClaims(ownerClaims);
        updateClaimsDialogViews();

        claimsDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Contact requests")
                .setView(claimsDialogBinding.getRoot())
                .setNegativeButton("Close", null)
                .create();
        claimsDialog.setOnDismissListener(d -> {
            claimsDialog = null;
            claimsDialogBinding = null;
            claimsAdapter = null;
        });
        claimsDialog.show();
    }

    private void updateClaimsDialogViews() {
        if (claimsDialogBinding == null) return;
        boolean empty = ownerClaims.isEmpty();
        claimsDialogBinding.rvClaims.setVisibility(empty ? View.GONE : View.VISIBLE);
        claimsDialogBinding.tvClaimsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void showOwnerContactDialog(Claim claim) {
        if (claim == null) return;

        DialogOwnerContactBinding dialogBinding =
                DialogOwnerContactBinding.inflate(getLayoutInflater());
        dialogBinding.etOwnerContact.setText(defaultOwnerContact);
        if (dialogBinding.etOwnerContact.getText() != null) {
            dialogBinding.etOwnerContact.setSelection(
                    dialogBinding.etOwnerContact.getText().length());
        }

        String claimant = emptyTo(claim.getClaimantName(), "this person");
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Approve contact request")
                .setMessage("Share contact details with " + claimant
                        + "? Only this person will see them.")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Approve & share", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String contact = text(dialogBinding.etOwnerContact);
                    if (contact.isEmpty()) {
                        dialogBinding.tilOwnerContact.setError(
                                "Add contact details before approving");
                        return;
                    }
                    dialogBinding.tilOwnerContact.setError(null);
                    dialog.dismiss();
                    approveClaim(claim, contact);
                }));

        dialog.show();
    }

    private void approveClaim(Claim claim, String contact) {
        claimRepository.updateClaimStatus(claim.getId(), Constants.CLAIM_APPROVED, contact,
                new ClaimRepository.ClaimCallback() {
                    @Override
                    public void onSuccess(Claim updated) {
                        if (currentItem != null
                                && !Constants.STATUS_CLAIMED.equalsIgnoreCase(currentItem.getStatus())) {
                            itemRepository.updateItemStatus(itemId, Constants.STATUS_CLAIMED,
                                    new ItemRepository.ItemCallback() {
                                        @Override
                                        public void onSuccess() {
                                            logActivity(Constants.ACTION_CLAIM_APPROVED,
                                                    "Claim approved: " + safeTrim(currentItem.getTitle()));
                                        }

                                        @Override
                                        public void onFailure(String errorMessage) {
                                            // The request is approved; the status update is best effort.
                                        }
                                    });
                        }
                        notifyClaimant(updated, true, contact);
                        Toast.makeText(ItemDetailActivity.this,
                                "Request approved and contact shared", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(ItemDetailActivity.this,
                                errorMessage == null ? "Unable to approve the request" : errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmRejectClaim(Claim claim) {
        if (claim == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Decline this request?")
                .setMessage("The person will be told that the owner declined. "
                        + "They will not see your contact details.")
                .setPositiveButton("Decline", (dialog, which) -> rejectClaim(claim))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rejectClaim(Claim claim) {
        claimRepository.updateClaimStatus(claim.getId(), Constants.CLAIM_REJECTED, "",
                new ClaimRepository.ClaimCallback() {
                    @Override
                    public void onSuccess(Claim updated) {
                        notifyClaimant(updated, false, "");
                        Toast.makeText(ItemDetailActivity.this,
                                "Request declined", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(ItemDetailActivity.this,
                                errorMessage == null ? "Unable to decline the request" : errorMessage,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void notifyClaimant(Claim claim, boolean approved, String contact) {
        if (claim == null || claim.getClaimantId() == null
                || claim.getClaimantId().trim().isEmpty()) {
            return;
        }

        String message = approved
                ? "The owner approved your request. You can reach them at " + contact + "."
                : "The owner declined your contact request for \""
                + safeTrim(claim.getItemTitle()) + "\".";

        notificationRepository.createNotification(new Notification(
                claim.getClaimantId(),
                authRepository.getCurrentUserId(),
                approved ? "Contact request approved" : "Contact request declined",
                message,
                claim.getItemId(),
                "claim"), null);
    }

    // ---------------------------------------------------------------------
    // Owner: edit / return / close / delete
    // ---------------------------------------------------------------------

    private void showEditDialog() {
        if (!isOwner || currentItem == null) return;

        DialogEditItemBinding dialogBinding = DialogEditItemBinding.inflate(getLayoutInflater());
        ArrayAdapter<String> categories = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, Constants.CATEGORIES);
        dialogBinding.actvEditCategory.setAdapter(categories);

        dialogBinding.etEditTitle.setText(safeTrim(currentItem.getTitle()));
        dialogBinding.actvEditCategory.setText(
                Constants.normaliseCategory(currentItem.getCategory()), false);
        dialogBinding.etEditDescription.setText(safeTrim(currentItem.getDescription()));
        dialogBinding.etEditLocation.setText(safeTrim(currentItem.getLocation()));
        dialogBinding.etEditColor.setText(safeTrim(currentItem.getPrimaryColor()));
        dialogBinding.etEditBrand.setText(safeTrim(currentItem.getBrand()));
        dialogBinding.etEditMaterial.setText(safeTrim(currentItem.getMaterial()));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Edit report")
                .setView(dialogBinding.getRoot())
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = text(dialogBinding.etEditTitle);
                    if (!ValidationUtils.isValidTitle(title)) {
                        dialogBinding.tilEditTitle.setError("Please enter a short item title");
                        return;
                    }
                    String description = text(dialogBinding.etEditDescription);
                    if (!ValidationUtils.isValidDescription(description)) {
                        dialogBinding.tilEditDescription.setError(
                                "Add a few identifying details");
                        return;
                    }
                    String location = text(dialogBinding.etEditLocation);
                    if (location.isEmpty()) {
                        dialogBinding.tilEditLocation.setError("Please enter a location");
                        return;
                    }

                    dialogBinding.tilEditTitle.setError(null);
                    dialogBinding.tilEditDescription.setError(null);
                    dialogBinding.tilEditLocation.setError(null);

                    dialog.dismiss();
                    saveEdits(title,
                            Constants.normaliseCategory(text(dialogBinding.actvEditCategory)),
                            description,
                            location,
                            text(dialogBinding.etEditColor),
                            text(dialogBinding.etEditBrand),
                            text(dialogBinding.etEditMaterial));
                }));

        dialog.show();
    }

    private void saveEdits(String title, String category, String description, String location,
                           String color, String brand, String material) {
        if (currentItem == null) return;

        currentItem.setTitle(title);
        currentItem.setCategory(category);
        currentItem.setDescription(description);
        currentItem.setLocation(location);
        currentItem.setPrimaryColor(color);
        currentItem.setBrand(brand);
        currentItem.setMaterial(material);

        itemRepository.updateItem(currentItem, new ItemRepository.ItemCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ItemDetailActivity.this,
                        "Report updated", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ItemDetailActivity.this,
                        errorMessage == null ? "Unable to update the report" : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmMarkReturned() {
        if (currentItem == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as returned?")
                .setMessage("This tells everyone the item has been returned to its owner. "
                        + "The report will stop appearing in active search.")
                .setPositiveButton("Mark returned", (dialog, which) -> markReturned())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void markReturned() {
        if (currentItem == null) return;
        binding.btnMarkReturned.setEnabled(false);

        itemRepository.markItemAsReturned(itemId, new ItemRepository.ItemCallback() {
            @Override
            public void onSuccess() {
                logActivity(Constants.ACTION_RETURNED,
                        "Returned: " + safeTrim(currentItem.getTitle()));
                notifyClaimantsItemReturned();
                Toast.makeText(ItemDetailActivity.this,
                        "Item marked as returned", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding != null) binding.btnMarkReturned.setEnabled(true);
                Toast.makeText(ItemDetailActivity.this,
                        errorMessage == null ? "Unable to update the report" : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void notifyClaimantsItemReturned() {
        if (currentItem == null) return;
        String uid = authRepository.getCurrentUserId();
        for (Claim claim : ownerClaims) {
            if (!Constants.CLAIM_APPROVED.equalsIgnoreCase(claim.getStatus())) continue;
            if (claim.getClaimantId() == null || claim.getClaimantId().trim().isEmpty()) continue;
            notificationRepository.createNotification(new Notification(
                    claim.getClaimantId(),
                    uid,
                    "Item marked as returned",
                    "\"" + safeTrim(currentItem.getTitle())
                            + "\" was marked as returned by the owner.",
                    itemId,
                    "status_change"), null);
        }
    }

    private void confirmCloseReport() {
        if (currentItem == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Close this report?")
                .setMessage("It will no longer appear in active search. You can still see it "
                        + "from your profile.")
                .setPositiveButton("Close report", (dialog, which) -> closeReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void closeReport() {
        itemRepository.closeReport(itemId, new ItemRepository.ItemCallback() {
            @Override
            public void onSuccess() {
                logActivity(Constants.ACTION_CLOSED,
                        "Closed: " + (currentItem != null
                                ? safeTrim(currentItem.getTitle()) : "report"));
                Toast.makeText(ItemDetailActivity.this,
                        "Report closed", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(ItemDetailActivity.this,
                        errorMessage == null ? "Unable to close the report" : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDeleteReport() {
        if (currentItem == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete this report?")
                .setMessage("This permanently removes the report and its photo. "
                        + "This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteReport())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteReport() {
        if (deleting || currentItem == null) return;
        deleting = true;
        binding.btnDeleteReport.setEnabled(false);

        String path = safeTrim(currentItem.getImagePath());
        String url = safeTrim(currentItem.getImageUrl());
        StorageRepository.DeleteCallback cleanup = new StorageRepository.DeleteCallback() {
            @Override
            public void onSuccess() {
                deleteItemDocument();
            }

            @Override
            public void onFailure(String errorMessage) {
                // Best effort: never block deleting the report on storage cleanup.
                deleteItemDocument();
            }
        };

        if (!path.isEmpty()) {
            storageRepository.deleteByPath(path, cleanup);
        } else if (!url.isEmpty()) {
            storageRepository.deleteByDownloadUrl(url, cleanup);
        } else {
            deleteItemDocument();
        }
    }

    private void deleteItemDocument() {
        itemRepository.deleteItem(itemId, new ItemRepository.ItemCallback() {
            @Override
            public void onSuccess() {
                Toast.makeText(ItemDetailActivity.this,
                        "Report deleted", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(String errorMessage) {
                deleting = false;
                if (binding != null) binding.btnDeleteReport.setEnabled(true);
                Toast.makeText(ItemDetailActivity.this,
                        errorMessage == null ? "Unable to delete the report" : errorMessage,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void logActivity(String actionType, String title) {
        ActivityRepository.getInstance().logActivity(new ActivityItem(
                authRepository.getCurrentUserId(),
                actionType,
                title,
                currentItem != null ? safeTrim(currentItem.getLocation()) : "",
                itemId), null);
    }

    // ---------------------------------------------------------------------
    // Menu / helpers
    // ---------------------------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_item_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_share) {
            shareItem();
            return true;
        } else if (itemId == R.id.action_more) {
            showMoreOptions();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMoreOptions() {
        String[] options = new String[]{"Copy item link", "Report this listing"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Options")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        copyItemLink();
                    } else if (which == 1) {
                        showReportAbuseDialog();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void copyItemLink() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && itemId != null) {
            ClipData clip = ClipData.newPlainText("Item Link", "laf://item/" + itemId);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Item link copied to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    private void showReportAbuseDialog() {
        if (currentItem == null) return;
        String[] reasons = new String[]{
                "Inappropriate image or text",
                "Spam or misleading information",
                "Harassment or personal info exposure",
                "Fraudulent claim or duplicate",
                "Other"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle("Report this listing")
                .setItems(reasons, (dialog, which) -> {
                    String selectedReason = reasons[which];
                    submitAbuseReport(selectedReason);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitAbuseReport(String reason) {
        String currentUid = authRepository.getCurrentUserId();
        ReportRepository.getInstance().submitReport(
                new ReportAbuse(
                        currentUid,
                        "item",
                        itemId,
                        reason,
                        "Reported by user for: " + reason
                ),
                new ReportRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        Toast.makeText(ItemDetailActivity.this,
                                "Thank you for reporting. Our moderation team has been notified.",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(ItemDetailActivity.this,
                                "Failed to submit report: " + errorMessage,
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void shareItem() {
        if (currentItem == null) return;

        String shareText = Constants.typeLabel(currentItem.getType()).toUpperCase() + ": "
                + safeTrim(currentItem.getTitle()) + "\n\n"
                + safeTrim(currentItem.getDescription()) + "\n\n"
                + "Location: " + safeTrim(currentItem.getLocation()) + "\n"
                + "Status: " + emptyTo(currentItem.getStatus(), "active") + "\n\n"
                + "Shared via Lost & Found";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, safeTrim(currentItem.getTitle()));
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private static String text(TextView view) {
        if (view == null || view.getText() == null) return "";
        return view.getText().toString().trim();
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private static String fallbackName(FirebaseUser user) {
        if (user == null) return "Campus user";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Campus user";
    }

    @Override
    protected void onDestroy() {
        if (itemRegistration != null) {
            itemRegistration.remove();
            itemRegistration = null;
        }
        if (ownerClaimsRegistration != null) {
            ownerClaimsRegistration.remove();
            ownerClaimsRegistration = null;
        }
        if (myClaimRegistration != null) {
            myClaimRegistration.remove();
            myClaimRegistration = null;
        }
        if (matchesRegistration != null) {
            matchesRegistration.remove();
            matchesRegistration = null;
        }
        if (claimsDialog != null) {
            claimsDialog.dismiss();
            claimsDialog = null;
        }
        if (matchesDialog != null) {
            matchesDialog.dismiss();
            matchesDialog = null;
        }
        claimsDialogBinding = null;
        claimsAdapter = null;
        matchesDialogBinding = null;
        matchesAdapter = null;
        super.onDestroy();
        binding = null;
    }
}
