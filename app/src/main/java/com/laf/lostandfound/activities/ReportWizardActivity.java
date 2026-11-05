package com.laf.lostandfound.activities;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseUser;
import com.laf.lostandfound.R;
import com.laf.lostandfound.databinding.ActivityReportWizardBinding;
import com.laf.lostandfound.models.ActivityItem;
import com.laf.lostandfound.models.Item;
import com.laf.lostandfound.models.MatchRecord;
import com.laf.lostandfound.models.Notification;
import com.laf.lostandfound.models.ReportDraft;
import com.laf.lostandfound.models.SearchResult;
import com.laf.lostandfound.models.User;
import com.laf.lostandfound.repositories.ActivityRepository;
import com.laf.lostandfound.repositories.AuthRepository;
import com.laf.lostandfound.repositories.ItemRepository;
import com.laf.lostandfound.repositories.MatchRepository;
import com.laf.lostandfound.repositories.NotificationRepository;
import com.laf.lostandfound.repositories.StorageRepository;
import com.laf.lostandfound.utils.AuthGuard;
import com.laf.lostandfound.utils.Constants;
import com.laf.lostandfound.utils.DraftManager;
import com.laf.lostandfound.utils.ImageAnalyzer;
import com.laf.lostandfound.utils.ImageLoader;
import com.laf.lostandfound.utils.ImageValidationUtils;
import com.laf.lostandfound.utils.MatchingEngine;
import com.laf.lostandfound.utils.ValidationUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Seven step guided report flow:
 * type -> photo -> upload -> AI analysis -> details -> when/where -> review.
 *
 * <p>Every meaningful state change is written to a local draft so the flow
 * survives rotation and process death. Publishing is idempotent because the
 * generated client request id is used as the Firestore document id.</p>
 */
public class ReportWizardActivity extends AppCompatActivity {

    private ActivityReportWizardBinding binding;

    private ItemRepository itemRepository;
    private AuthRepository authRepository;
    private StorageRepository storageRepository;
    private ActivityRepository activityRepository;
    private NotificationRepository notificationRepository;
    private MatchRepository matchRepository;

    private int currentStep = 1;

    private String selectedType = Constants.TYPE_LOST;
    private Uri selectedImageUri;
    private Uri pendingCameraUri;

    private String uploadedImageUrl = "";
    private String uploadedImagePath = "";
    private String uploadedForUri = "";

    private String clientRequestId;
    private final Calendar selectedCalendar = Calendar.getInstance();

    private String aiStatus = "";
    private double aiConfidence = 0;
    private String aiModel = "";
    private boolean aiFailed = false;
    private boolean aiSkipped = false;
    private final List<String> aiFeatures = new ArrayList<>();
    private final List<String> aiTags = new ArrayList<>();
    private final List<String> aiKeywords = new ArrayList<>();
    private final List<String> aiSecondaryColors = new ArrayList<>();
    private double latitude = 0.0;
    private double longitude = 0.0;

    private boolean publishing = false;
    private boolean draftRestored = false;
    private boolean draftCleared = false;
    private long lastClickTime = 0;

    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                boolean fineGranted = Boolean.TRUE.equals(permissions.get(android.Manifest.permission.ACCESS_FINE_LOCATION));
                boolean coarseGranted = Boolean.TRUE.equals(permissions.get(android.Manifest.permission.ACCESS_COARSE_LOCATION));
                if (fineGranted || coarseGranted) {
                    fetchLocationCoordinates();
                } else {
                    Toast.makeText(this, "Location permission denied. You can enter the location manually.", Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) acceptImage(uri);
            });

    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success && pendingCameraUri != null) {
                    acceptImage(pendingCameraUri);
                } else if (!success) {
                    Toast.makeText(this, "Photo was not captured", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AuthGuard.requireSignedIn(this)) {
            return;
        }
        binding = ActivityReportWizardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        itemRepository = ItemRepository.getInstance();
        authRepository = AuthRepository.getInstance();
        storageRepository = StorageRepository.getInstance();
        activityRepository = ActivityRepository.getInstance();
        notificationRepository = NotificationRepository.getInstance();
        matchRepository = MatchRepository.getInstance();

        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        clientRequestId = UUID.randomUUID().toString();

        setupCategoryDropdown();
        setupStep1Selection();
        setupStep2Pickers();
        setupStep6Pickers();
        setupNavigationButtons();
        setupBackHandling();
        restoreDraft();
        updateStepUI();
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------

    private void setupCategoryDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, Constants.CATEGORIES);
        binding.actvWizardCategory.setAdapter(adapter);
        binding.actvWizardCategory.setText(Constants.CATEGORY_OTHER, false);
    }

    private void setupStep1Selection() {
        binding.cardLost.setOnClickListener(v -> selectType(Constants.TYPE_LOST));
        binding.cardFound.setOnClickListener(v -> selectType(Constants.TYPE_FOUND));
        selectType(Constants.TYPE_LOST);
    }

    private void selectType(String type) {
        selectedType = type;
        boolean lost = Constants.TYPE_LOST.equalsIgnoreCase(type);
        binding.rbLost.setChecked(lost);
        binding.rbFound.setChecked(!lost);
        binding.cardLost.setStrokeColor(getColor(lost ? R.color.lost_red : android.R.color.transparent));
        binding.cardFound.setStrokeColor(getColor(!lost ? R.color.found_green : android.R.color.transparent));
        saveDraft();
    }

    private void setupStep2Pickers() {
        binding.btnGalleryPick.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        binding.cardPhotoPicker.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        binding.btnCameraPick.setOnClickListener(v -> openCamera());
    }

    private void openCamera() {
        Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "No camera app available. Choose from the gallery instead.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File directory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (directory == null) directory = getCacheDir();
            String name = "LAF_" + System.currentTimeMillis();
            File file = File.createTempFile(name, ".jpg", directory);
            pendingCameraUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (IOException e) {
            Toast.makeText(this, "Unable to prepare the camera. Try the gallery.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void acceptImage(@NonNull Uri uri) {
        ImageValidationUtils.Result validation =
                ImageValidationUtils.validate(this, uri, Constants.MAX_IMAGE_BYTES);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getError(), Toast.LENGTH_LONG).show();
            return;
        }

        selectedImageUri = uri;
        uploadedImageUrl = "";
        uploadedImagePath = "";
        uploadedForUri = "";
        aiStatus = "";
        aiFailed = false;
        aiSkipped = false;
        aiFeatures.clear();
        aiTags.clear();
        aiKeywords.clear();
        binding.tvAiBanner.setVisibility(View.GONE);

        binding.ivStepPhotoPreview.setVisibility(View.VISIBLE);
        binding.llPhotoPrompt.setVisibility(View.GONE);
        ImageLoader.loadImage(this, uri.toString(), binding.ivStepPhotoPreview, 20);
        saveDraft();
    }

    private void setupStep6Pickers() {
        updateDateButtons();

        binding.btnPickDate.setOnClickListener(v -> {
            DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
                selectedCalendar.set(Calendar.YEAR, year);
                selectedCalendar.set(Calendar.MONTH, month);
                selectedCalendar.set(Calendar.DAY_OF_MONTH, day);
                updateDateButtons();
                saveDraft();
            }, selectedCalendar.get(Calendar.YEAR), selectedCalendar.get(Calendar.MONTH),
                    selectedCalendar.get(Calendar.DAY_OF_MONTH));
            dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
            dialog.show();
        });

        binding.btnPickTime.setOnClickListener(v -> {
            TimePickerDialog dialog = new TimePickerDialog(this, (view, hour, minute) -> {
                selectedCalendar.set(Calendar.HOUR_OF_DAY, hour);
                selectedCalendar.set(Calendar.MINUTE, minute);
                updateDateButtons();
                saveDraft();
            }, selectedCalendar.get(Calendar.HOUR_OF_DAY), selectedCalendar.get(Calendar.MINUTE), false);
            dialog.show();
        });

        binding.btnDetectLocation.setOnClickListener(v -> detectLocation());
    }

    private void detectLocation() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocationCoordinates();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void fetchLocationCoordinates() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;

        Location bestLocation = null;
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                bestLocation = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (bestLocation == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                bestLocation = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
        } catch (SecurityException ignored) {
        }

        if (bestLocation != null) {
            applyFoundLocation(bestLocation.getLatitude(), bestLocation.getLongitude());
        } else {
            Toast.makeText(this, "Acquiring location... Please ensure GPS is turned on.", Toast.LENGTH_SHORT).show();
            try {
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, new LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location location) {
                            applyFoundLocation(location.getLatitude(), location.getLongitude());
                        }
                    }, Looper.getMainLooper());
                }
            } catch (SecurityException ignored) {
            }
        }
    }

    private void applyFoundLocation(double lat, double lng) {
        this.latitude = lat;
        this.longitude = lng;

        if (binding != null) {
            String coords = String.format(Locale.getDefault(), "%.4f°, %.4f°", lat, lng);
            binding.tvLocationCoordinates.setText(coords);
            binding.tvLocationCoordinates.setVisibility(View.VISIBLE);

            if (text(binding.etWizardLocation).isEmpty()) {
                try {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address addr = addresses.get(0);
                        StringBuilder sb = new StringBuilder();
                        if (addr.getFeatureName() != null) sb.append(addr.getFeatureName()).append(", ");
                        if (addr.getSubLocality() != null) sb.append(addr.getSubLocality()).append(", ");
                        if (addr.getLocality() != null) sb.append(addr.getLocality());
                        String name = sb.toString().replaceAll(", $", "");
                        if (!name.isEmpty()) {
                            binding.etWizardLocation.setText(name);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        Toast.makeText(this, "Coordinates pinned: " + String.format(Locale.getDefault(), "%.4f, %.4f", lat, lng), Toast.LENGTH_SHORT).show();
        saveDraft();
    }

    private void updateDateButtons() {
        binding.btnPickDate.setText("Date: " + dateFormat.format(selectedCalendar.getTime()));
        binding.btnPickTime.setText("Time: " + timeFormat.format(selectedCalendar.getTime()));
    }

    private void setupNavigationButtons() {
        binding.btnWizardPrev.setOnClickListener(v -> goBack());
        binding.btnWizardNext.setOnClickListener(v -> handleNextStep());
        binding.btnSkipAi.setOnClickListener(v -> {
            if (currentStep != 4) return;
            aiSkipped = true;
            aiFailed = true;
            aiStatus = "manual";
            moveToStep(5);
        });
    }

    private void setupBackHandling() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                goBack();
            }
        });
    }

    // ---------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------

    private void goBack() {
        switch (currentStep) {
            case 1:
                confirmDiscard();
                break;
            case 2:
                moveToStep(1);
                break;
            case 3:
            case 4:
                Toast.makeText(this, "Please wait for this step to finish", Toast.LENGTH_SHORT).show();
                break;
            case 5:
                moveToStep(2);
                break;
            case 6:
                moveToStep(5);
                break;
            case 7:
                moveToStep(6);
                break;
            default:
                break;
        }
    }

    private void confirmDiscard() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Discard this report?")
                .setMessage("Your draft is kept on this device. You can continue later, or discard it now.")
                .setPositiveButton("Discard", (dialog, which) -> {
                    draftCleared = true;
                    DraftManager.clear(this);
                    finish();
                })
                .setNegativeButton("Keep draft", (dialog, which) -> finish())
                .setNeutralButton("Keep editing", null)
                .show();
    }

    private void handleNextStep() {
        if (SystemClock.elapsedRealtime() - lastClickTime < 600) return;
        lastClickTime = SystemClock.elapsedRealtime();

        switch (currentStep) {
            case 1:
                moveToStep(2);
                break;
            case 2:
                if (selectedImageUri == null && uploadedImageUrl.isEmpty()) {
                    Toast.makeText(this, "Please select an item photo to continue",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!uploadedImageUrl.isEmpty()
                        && (selectedImageUri == null
                        || selectedImageUri.toString().equals(uploadedForUri))) {
                    if ("completed".equals(aiStatus)) {
                        moveToStep(5);
                    } else if ("failed".equals(aiStatus) || "manual".equals(aiStatus) || aiStatus.isEmpty()) {
                        moveToStep(4);
                        startAiAnalysis();
                    } else {
                        moveToStep(5);
                    }
                    return;
                }
                moveToStep(3);
                startCloudUpload();
                break;
            case 3:
            case 4:
                break;
            case 5:
                if (!validateDetailsStep()) return;
                moveToStep(6);
                break;
            case 6:
                if (!validateWhenWhereStep()) return;
                populateReviewStep();
                moveToStep(7);
                break;
            case 7:
                publishReport();
                break;
            default:
                break;
        }
    }

    private boolean validateDetailsStep() {
        String title = text(binding.etWizardTitle);
        if (!ValidationUtils.isValidTitle(title)) {
            binding.etWizardTitle.setError("Please enter a short item title");
            binding.etWizardTitle.requestFocus();
            return false;
        }

        String category = text(binding.actvWizardCategory);
        if (category.isEmpty()) {
            binding.actvWizardCategory.setError("Choose a category");
            binding.actvWizardCategory.requestFocus();
            return false;
        }

        String description = text(binding.etWizardDescription);
        if (!ValidationUtils.isValidDescription(description)) {
            binding.etWizardDescription.setError("Add a few identifying details");
            binding.etWizardDescription.requestFocus();
            return false;
        }
        return true;
    }

    private boolean validateWhenWhereStep() {
        String location = text(binding.etWizardLocation);
        if (location.isEmpty()) {
            binding.etWizardLocation.setError("Please enter a campus location");
            binding.etWizardLocation.requestFocus();
            return false;
        }
        if (selectedCalendar.getTimeInMillis() > System.currentTimeMillis() + 5 * 60_000L) {
            Toast.makeText(this, "The date and time cannot be in the future",
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void moveToStep(int step) {
        currentStep = Math.max(1, Math.min(Constants.WIZARD_STEPS, step));
        updateStepUI();
        saveDraft();
    }

    private void updateStepUI() {
        binding.progressStep.setProgress(currentStep);
        binding.tvStepPercentage.setText(Math.round((currentStep / (float) Constants.WIZARD_STEPS) * 100) + "%");

        binding.layoutStep1.setVisibility(currentStep == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(currentStep == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(currentStep == 3 ? View.VISIBLE : View.GONE);
        binding.layoutStep4.setVisibility(currentStep == 4 ? View.VISIBLE : View.GONE);
        binding.layoutStep5.setVisibility(currentStep == 5 ? View.VISIBLE : View.GONE);
        binding.layoutStep6.setVisibility(currentStep == 6 ? View.VISIBLE : View.GONE);
        binding.layoutStep7.setVisibility(currentStep == 7 ? View.VISIBLE : View.GONE);

        binding.btnWizardPrev.setText(currentStep == 1 ? "Cancel" : "Back");
        binding.btnWizardPrev.setEnabled(currentStep != 3 && currentStep != 4 && !publishing);
        binding.btnWizardNext.setEnabled(currentStep != 3 && currentStep != 4 && !publishing);

        switch (currentStep) {
            case 1:
                binding.tvStepTitle.setText("Step 1 of 7: Select Type");
                binding.btnWizardNext.setText("Continue →");
                break;
            case 2:
                binding.tvStepTitle.setText("Step 2 of 7: Add Photo");
                binding.btnWizardNext.setText("Upload & Scan →");
                break;
            case 3:
                binding.tvStepTitle.setText("Step 3 of 7: Secure Upload");
                binding.btnWizardNext.setText("Uploading...");
                break;
            case 4:
                binding.tvStepTitle.setText("Step 4 of 7: AI Vision");
                binding.btnWizardNext.setText("Analyzing...");
                break;
            case 5:
                binding.tvStepTitle.setText("Step 5 of 7: Review Details");
                binding.btnWizardNext.setText("Continue →");
                break;
            case 6:
                binding.tvStepTitle.setText("Step 6 of 7: Date & Place");
                binding.btnWizardNext.setText("Review →");
                break;
            case 7:
                binding.tvStepTitle.setText("Step 7 of 7: Final Review");
                binding.btnWizardNext.setText(publishing ? "Publishing..." : "Publish Report ✓");
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------------
    // Upload + AI
    // ---------------------------------------------------------------------

    private void startCloudUpload() {
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
            moveToStep(2);
            return;
        }

        binding.pbCloudUpload.setIndeterminate(false);
        binding.pbCloudUpload.setProgress(0);
        binding.tvUploadPercent.setText("0%");
        binding.tvUploadStatus.setText("Uploading securely...");

        storageRepository.uploadItemImage(selectedImageUri, percent -> {
            if (binding == null) return;
            binding.pbCloudUpload.setProgress(percent);
            binding.tvUploadPercent.setText(percent + "%");
        }, new StorageRepository.UploadCallback() {
            @Override
            public void onSuccess(StorageRepository.UploadResult result) {
                if (binding == null) return;
                uploadedImageUrl = result.getDownloadUrl();
                uploadedImagePath = result.getStoragePath();
                uploadedForUri = selectedImageUri != null ? selectedImageUri.toString() : "";
                binding.pbCloudUpload.setProgress(100);
                binding.tvUploadPercent.setText("100%");
                binding.tvUploadStatus.setText("Upload complete");
                moveToStep(4);
                startAiAnalysis();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null) return;
                new MaterialAlertDialogBuilder(ReportWizardActivity.this)
                        .setTitle("Upload failed")
                        .setMessage(errorMessage)
                        .setCancelable(false)
                        .setPositiveButton("Retry", (dialog, which) -> startCloudUpload())
                        .setNegativeButton("Choose another photo", (dialog, which) -> moveToStep(2))
                        .show();
            }
        });
    }

    private void startAiAnalysis() {
        if (selectedImageUri == null) {
            aiFailed = true;
            aiStatus = "manual";
            moveToStep(5);
            showAiBanner("The photo is already uploaded, but it can no longer be analyzed. "
                    + "Please enter the details manually.", true);
            return;
        }

        aiSkipped = false;
        aiFailed = false;
        aiStatus = "";
        binding.tvAiScanningStatus.setText(
                "Detecting the item category, dominant colors, materials and identifying features.");

        final Uri analysisUri = selectedImageUri;
        ImageAnalyzer.analyzeItemAsync(this, analysisUri, new ImageAnalyzer.AnalysisCallback() {
            @Override
            public void onSuccess(ImageAnalyzer.AnalysisResult result) {
                if (binding == null || aiSkipped || currentStep != 4) return;
                applyAnalysis(result);
                moveToStep(5);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (binding == null || aiSkipped || currentStep != 4) return;
                aiFailed = true;
                aiStatus = "failed";
                moveToStep(5);
                showAiBanner(errorMessage + " You can enter the details manually.", true);
            }
        });
    }

    private void applyAnalysis(ImageAnalyzer.AnalysisResult result) {
        if (result == null) {
            aiStatus = "failed";
            aiFailed = true;
            showAiBanner("The AI service returned no details. Please fill them in manually.", true);
            return;
        }

        aiStatus = "completed";
        aiFailed = false;
        aiConfidence = result.getConfidence();
        aiModel = result.getModel();

        aiFeatures.clear();
        aiFeatures.addAll(result.getFeatureList());
        aiTags.clear();
        aiTags.addAll(result.getTags());
        aiKeywords.clear();
        aiKeywords.addAll(result.getKeywords());
        aiSecondaryColors.clear();
        if (result.getSecondaryColors() != null) {
            aiSecondaryColors.addAll(result.getSecondaryColors());
        }

        if (text(binding.etWizardTitle).isEmpty() && result.getItemType() != null) {
            binding.etWizardTitle.setText(result.getItemType());
        }
        if (text(binding.actvWizardCategory).isEmpty()
                || Constants.CATEGORY_OTHER.equalsIgnoreCase(text(binding.actvWizardCategory))) {
            binding.actvWizardCategory.setText(
                    Constants.normaliseCategory(result.getSuggestedCategory()), false);
        }
        if (text(binding.etWizardColor).isEmpty() && result.getPrimaryColor() != null) {
            binding.etWizardColor.setText(result.getPrimaryColor());
        }
        if (text(binding.etWizardBrand).isEmpty() && result.getBrand() != null) {
            binding.etWizardBrand.setText(result.getBrand());
        }
        if (text(binding.etWizardMaterial).isEmpty() && result.getMaterial() != null) {
            binding.etWizardMaterial.setText(result.getMaterial());
        }
        if (text(binding.etWizardDescription).isEmpty() && !aiFeatures.isEmpty()) {
            StringBuilder builder = new StringBuilder();
            if (result.getPrimaryColor() != null && !result.getPrimaryColor().isEmpty()) {
                builder.append("Color: ").append(result.getPrimaryColor()).append(". ");
            }
            builder.append("Features: ");
            for (int i = 0; i < aiFeatures.size(); i++) {
                if (i > 0) builder.append(", ");
                builder.append(aiFeatures.get(i));
            }
            binding.etWizardDescription.setText(builder.toString().trim());
        }

        String banner = aiModel.isEmpty()
                ? "AI tagged this photo. Please verify the details."
                : "AI tagged this photo (" + Math.round(aiConfidence) + "% · " + aiModel
                + "). Please verify the details.";
        showAiBanner(banner, false);
    }

    private void showAiBanner(String message, boolean warning) {
        binding.tvAiBanner.setText(message);
        binding.tvAiBanner.setBackgroundResource(
                warning ? R.drawable.bg_warning_banner : R.drawable.bg_success_banner);
        binding.tvAiBanner.setTextColor(warning ? 0xFF8A5A19 : 0xFF1B4332);
        binding.tvAiBanner.setVisibility(View.VISIBLE);
    }

    // ---------------------------------------------------------------------
    // Review + publish
    // ---------------------------------------------------------------------

    private void populateReviewStep() {
        String preview = !uploadedImageUrl.isEmpty()
                ? uploadedImageUrl
                : (selectedImageUri != null ? selectedImageUri.toString() : "");
        ImageLoader.loadImage(this, preview, binding.ivReviewImage, 16);

        boolean found = Constants.TYPE_FOUND.equalsIgnoreCase(selectedType);
        binding.tvReviewBadge.setText(found ? "FOUND" : "LOST");
        binding.tvReviewBadge.setBackgroundResource(
                found ? R.drawable.bg_badge_found : R.drawable.bg_badge_lost);

        binding.tvReviewTitle.setText(text(binding.etWizardTitle));
        binding.tvReviewCategory.setText("Category: " + text(binding.actvWizardCategory));
        String locDisplay = "Location: " + text(binding.etWizardLocation);
        if (latitude != 0.0 || longitude != 0.0) {
            locDisplay += String.format(Locale.getDefault(), " (%.4f°, %.4f°)", latitude, longitude);
        }
        binding.tvReviewLocation.setText(locDisplay);
        binding.tvReviewDate.setText("When: " + dateFormat.format(selectedCalendar.getTime())
                + " · " + timeFormat.format(selectedCalendar.getTime()));
        binding.tvReviewDescription.setText(text(binding.etWizardDescription));

        if ("completed".equals(aiStatus)) {
            binding.tvReviewAi.setText("AI confidence " + Math.round(aiConfidence) + "%"
                    + (aiModel.isEmpty() ? "" : " · " + aiModel));
            binding.tvReviewAi.setVisibility(View.VISIBLE);
        } else if ("failed".equals(aiStatus)) {
            binding.tvReviewAi.setText("AI analysis failed — details entered manually");
            binding.tvReviewAi.setVisibility(View.VISIBLE);
        } else if ("manual".equals(aiStatus)) {
            binding.tvReviewAi.setText("AI skipped — details entered manually");
            binding.tvReviewAi.setVisibility(View.VISIBLE);
        } else {
            binding.tvReviewAi.setVisibility(View.GONE);
        }
    }

    private void publishReport() {
        if (publishing) return;
        publishing = true;
        updateStepUI();
        binding.btnWizardPrev.setEnabled(false);
        binding.btnWizardNext.setEnabled(false);

        FirebaseUser currentUser = authRepository.getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : "";
        if (uid.isEmpty()) {
            publishing = false;
            updateStepUI();
            AuthGuard.requireSignedIn(this);
            return;
        }

        authRepository.getUserProfile(uid, new AuthRepository.UserCallback() {
            @Override
            public void onSuccess(User user) {
                String name = user != null && user.getDisplayName() != null
                        && !user.getDisplayName().trim().isEmpty()
                        ? user.getDisplayName().trim()
                        : fallbackName(currentUser);
                boolean verified = user != null && user.isVerified();
                publishWithProfile(uid, name, verified);
            }

            @Override
            public void onFailure(String errorMessage) {
                publishWithProfile(uid, fallbackName(currentUser),
                        currentUser != null && currentUser.isEmailVerified());
            }
        });
    }

    private void publishWithProfile(String uid, String userName, boolean verified) {
        Item item = buildItem(uid, userName, verified);

        itemRepository.addItem(item, new ItemRepository.ItemCallback() {
            @Override
            public void onSuccess() {
                // The report is live; never recreate its draft from onPause.
                draftCleared = true;
                DraftManager.clear(ReportWizardActivity.this);
                logPublishedActivity(item);
                runAutomaticMatching(item);
                showSuccessDialog();
            }

            @Override
            public void onFailure(String errorMessage) {
                publishing = false;
                updateStepUI();
                new MaterialAlertDialogBuilder(ReportWizardActivity.this)
                        .setTitle("Could not publish")
                        .setMessage(errorMessage)
                        .setPositiveButton("Retry", (dialog, which) -> publishReport())
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
    }

    private Item buildItem(String uid, String userName, boolean verified) {
        Item item = new Item();
        item.setUserId(uid);
        item.setUserName(userName);
        item.setUserVerified(verified);
        item.setType(selectedType);
        item.setTitle(text(binding.etWizardTitle));
        item.setCategory(Constants.normaliseCategory(text(binding.actvWizardCategory)));
        item.setPrimaryColor(text(binding.etWizardColor));
        item.setSecondaryColors(new ArrayList<>(aiSecondaryColors));
        item.setBrand(text(binding.etWizardBrand));
        item.setMaterial(text(binding.etWizardMaterial));
        item.setDescription(text(binding.etWizardDescription));
        item.setLocation(text(binding.etWizardLocation));
        item.setLatitude(latitude);
        item.setLongitude(longitude);
        item.setImageUrl(uploadedImageUrl);
        item.setImagePath(uploadedImagePath);
        item.setStorageProvider(Constants.STORAGE_PROVIDER_FIREBASE);
        item.setIncidentAt(selectedCalendar.getTimeInMillis());
        item.setStatus(Constants.STATUS_ACTIVE);
        item.setFeatures(new ArrayList<>(aiFeatures));
        item.setTags(new ArrayList<>(aiTags));
        item.setKeywords(new ArrayList<>(aiKeywords));
        item.setAiConfidence(aiConfidence);
        item.setAiModel(aiModel);
        item.setAiStatus(aiStatus.isEmpty() ? "manual" : aiStatus);
        item.setClientRequestId(clientRequestId);
        return item;
    }

    private void logPublishedActivity(Item item) {
        String uid = authRepository.getCurrentUserId();
        String actionType = Constants.TYPE_FOUND.equalsIgnoreCase(selectedType)
                ? Constants.ACTION_REPORTED_FOUND
                : Constants.ACTION_REPORTED_LOST;
        String title = (Constants.TYPE_FOUND.equalsIgnoreCase(selectedType)
                ? "Reported found: " : "Reported lost: ") + item.getTitle();
        activityRepository.logActivity(
                new ActivityItem(uid, actionType, title, item.getLocation(), item.getId()), null);
    }

    private void runAutomaticMatching(Item publishedItem) {
        String oppositeType = Constants.TYPE_FOUND.equalsIgnoreCase(publishedItem.getType())
                ? Constants.TYPE_LOST
                : Constants.TYPE_FOUND;

        itemRepository.getActiveItemsByType(oppositeType, new ItemRepository.ItemListCallback() {
            @Override
            public void onSuccess(List<Item> candidates) {
                List<SearchResult> matches = MatchingEngine.findMatches(publishedItem, candidates);
                int sent = 0;
                String uid = authRepository.getCurrentUserId();
                for (SearchResult result : matches) {
                    if (sent >= 3) break;
                    if (result.getMatchPercentage() < Constants.MATCH_NOTIFICATION_THRESHOLD) break;
                    Item candidate = result.getItem();
                    if (candidate == null) continue;

                    MatchRecord record = new MatchRecord();
                    record.setItemId(publishedItem.getId());
                    record.setMatchedItemId(candidate.getId());
                    record.setOwnerId(uid);
                    record.setMatchedOwnerId(candidate.getUserId());
                    record.setItemTitle(publishedItem.getTitle());
                    record.setMatchedItemTitle(candidate.getTitle());
                    record.setScore(result.getMatchPercentage());
                    matchRepository.saveMatch(record, null);

                    notificationRepository.createNotification(new Notification(
                            uid, uid,
                            "Potential match found (" + result.getMatchPercentage() + "%)",
                            "A " + candidate.getType() + " report \"" + candidate.getTitle()
                                    + "\" may match your new report.",
                            candidate.getId(), "match"), null);

                    activityRepository.logActivity(new ActivityItem(
                            uid,
                            Constants.ACTION_MATCHED,
                            "Found possible match (" + result.getMatchPercentage() + "%): " + candidate.getTitle(),
                            candidate.getLocation(),
                            publishedItem.getId()), null);

                    if (candidate.getUserId() != null && !candidate.getUserId().isEmpty()) {
                        MatchRecord candidateRecord = new MatchRecord();
                        candidateRecord.setItemId(candidate.getId());
                        candidateRecord.setMatchedItemId(publishedItem.getId());
                        candidateRecord.setOwnerId(candidate.getUserId());
                        candidateRecord.setMatchedOwnerId(uid);
                        candidateRecord.setItemTitle(candidate.getTitle());
                        candidateRecord.setMatchedItemTitle(publishedItem.getTitle());
                        candidateRecord.setScore(result.getMatchPercentage());
                        matchRepository.saveMatch(candidateRecord, null);

                        notificationRepository.createNotification(new Notification(
                                candidate.getUserId(), uid,
                                "A new report may match your item",
                                "Someone reported \"" + publishedItem.getTitle()
                                        + "\", which may match your item \"" + candidate.getTitle()
                                        + "\" (" + result.getMatchPercentage() + "% match).",
                                publishedItem.getId(), "match"), null);

                        activityRepository.logActivity(new ActivityItem(
                                candidate.getUserId(),
                                Constants.ACTION_MATCHED,
                                "New report may match your item (" + result.getMatchPercentage() + "%): " + publishedItem.getTitle(),
                                publishedItem.getLocation(),
                                candidate.getId()), null);
                    }
                    sent++;
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                // Matching is best effort; the report is already published.
            }
        });
    }

    private void showSuccessDialog() {
        publishing = false;
        updateStepUI();
        new MaterialAlertDialogBuilder(this)
                .setTitle("Report published")
                .setMessage("Your " + selectedType + " report is live. We will alert you if a "
                        + "matching item is reported. The photo is stored privately and your "
                        + "phone number is never shown publicly.")
                .setCancelable(false)
                .setPositiveButton("Done", (dialog, which) -> finish())
                .show();
    }

    private static String fallbackName(FirebaseUser user) {
        if (user == null) return "Campus User";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        String email = user.getEmail();
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return "Campus User";
    }

    // ---------------------------------------------------------------------
    // Draft persistence
    // ---------------------------------------------------------------------

    private void saveDraft() {
        // Do not write before the stored draft has been loaded (that would
        // overwrite it with blank defaults during startup) and never recreate
        // a draft after it has been published or explicitly discarded.
        if (binding == null || !draftRestored || draftCleared) return;
        ReportDraft draft = new ReportDraft();
        draft.setStep(currentStep);
        draft.setType(selectedType);
        draft.setImageUriString(selectedImageUri != null ? selectedImageUri.toString() : "");
        draft.setImageUrl(uploadedImageUrl);
        draft.setImagePath(uploadedImagePath);
        draft.setClientRequestId(clientRequestId);
        draft.setTitle(text(binding.etWizardTitle));
        draft.setCategory(text(binding.actvWizardCategory));
        draft.setPrimaryColor(text(binding.etWizardColor));
        draft.setSecondaryColors(new ArrayList<>(aiSecondaryColors));
        draft.setBrand(text(binding.etWizardBrand));
        draft.setMaterial(text(binding.etWizardMaterial));
        draft.setDescription(text(binding.etWizardDescription));
        draft.setLocation(text(binding.etWizardLocation));
        draft.setLatitude(latitude);
        draft.setLongitude(longitude);
        draft.setIncidentAt(selectedCalendar.getTimeInMillis());
        draft.setFeatures(new ArrayList<>(aiFeatures));
        draft.setTags(new ArrayList<>(aiTags));
        draft.setKeywords(new ArrayList<>(aiKeywords));
        draft.setAiConfidence(aiConfidence);
        draft.setAiModel(aiModel);
        draft.setAiStatus(aiStatus);
        draft.setAiFailed(aiFailed);
        DraftManager.save(this, draft);
    }

    private void restoreDraft() {
        ReportDraft draft = DraftManager.load(this);
        if (draft == null) {
            draftRestored = true;
            selectType(Constants.TYPE_LOST);
            return;
        }

        selectedType = Constants.TYPE_FOUND.equalsIgnoreCase(draft.getType())
                ? Constants.TYPE_FOUND : Constants.TYPE_LOST;
        clientRequestId = draft.getClientRequestId() == null || draft.getClientRequestId().isEmpty()
                ? UUID.randomUUID().toString() : draft.getClientRequestId();

        uploadedImageUrl = draft.getImageUrl() == null ? "" : draft.getImageUrl();
        uploadedImagePath = draft.getImagePath() == null ? "" : draft.getImagePath();
        uploadedForUri = draft.getImageUriString() == null ? "" : draft.getImageUriString();

        if (draft.getImageUriString() != null && !draft.getImageUriString().isEmpty()) {
            try {
                selectedImageUri = Uri.parse(draft.getImageUriString());
            } catch (Exception ignored) {
                selectedImageUri = null;
            }
        }

        binding.etWizardTitle.setText(draft.getTitle());
        binding.actvWizardCategory.setText(
                Constants.normaliseCategory(draft.getCategory()), false);
        binding.etWizardColor.setText(draft.getPrimaryColor());
        binding.etWizardBrand.setText(draft.getBrand());
        binding.etWizardMaterial.setText(draft.getMaterial());
        binding.etWizardDescription.setText(draft.getDescription());
        binding.etWizardLocation.setText(draft.getLocation());

        if (draft.getIncidentAt() > 0) {
            selectedCalendar.setTimeInMillis(draft.getIncidentAt());
        }
        updateDateButtons();

        aiFeatures.clear();
        aiFeatures.addAll(draft.getFeatures());
        aiTags.clear();
        aiTags.addAll(draft.getTags());
        aiKeywords.clear();
        aiKeywords.addAll(draft.getKeywords());
        aiSecondaryColors.clear();
        if (draft.getSecondaryColors() != null) {
            aiSecondaryColors.addAll(draft.getSecondaryColors());
        }
        latitude = draft.getLatitude();
        longitude = draft.getLongitude();
        if (latitude != 0.0 || longitude != 0.0) {
            binding.tvLocationCoordinates.setText(
                    String.format(Locale.getDefault(), "%.4f°, %.4f°", latitude, longitude));
            binding.tvLocationCoordinates.setVisibility(View.VISIBLE);
        }
        aiConfidence = draft.getAiConfidence();
        aiModel = draft.getAiModel() == null ? "" : draft.getAiModel();
        aiStatus = draft.getAiStatus() == null ? "" : draft.getAiStatus();
        aiFailed = draft.isAiFailed();

        if (selectedImageUri != null) {
            binding.ivStepPhotoPreview.setVisibility(View.VISIBLE);
            binding.llPhotoPrompt.setVisibility(View.GONE);
            ImageLoader.loadImage(this, selectedImageUri.toString(),
                    binding.ivStepPhotoPreview, 20);
        } else if (!uploadedImageUrl.isEmpty()) {
            binding.ivStepPhotoPreview.setVisibility(View.VISIBLE);
            binding.llPhotoPrompt.setVisibility(View.GONE);
            ImageLoader.loadImage(this, uploadedImageUrl, binding.ivStepPhotoPreview, 20);
        }

        int step = Math.max(1, Math.min(Constants.WIZARD_STEPS, draft.getStep()));
        if (step == 3 || step == 4) step = 2;
        if (step > 2 && selectedImageUri == null && uploadedImageUrl.isEmpty()) step = 1;
        currentStep = step;
        // Enable persistence only now that the restored state is applied.
        draftRestored = true;
        selectType(selectedType);
        if ("failed".equals(aiStatus)) {
            showAiBanner("AI analysis was unavailable. Please verify the details manually.", true);
        }
    }

    private void saveDraftAndFinish() {
        saveDraft();
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveDraft();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            goBack();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    private static String text(android.widget.TextView view) {
        if (view == null || view.getText() == null) return "";
        return view.getText().toString().trim();
    }
}
