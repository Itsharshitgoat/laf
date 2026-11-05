# Lost & Found — Campus Item Recovery System

![Android CI](https://github.com/Itsharshitgoat/laf/actions/workflows/android.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

A native Android application designed to help university campuses and communities report, search, match, and return lost items securely and efficiently.

---

## 🌟 Highlights & Features

- **Authentication & Verification**:
  - Email and password authentication via Firebase Auth.
  - Verification enforcement with one-tap resend functionality.
  - Password reset dialog and auth-state persistence.

- **7-Step Guided Report Wizard**:
  - Type selection (`Lost` or `Found`).
  - Secure camera capture with FileProvider and gallery photo picker.
  - Automatic image compression and validation (bounds, orientation, aspect ratio).
  - Background Firebase Storage upload with live progress reporting.
  - Automated AI item classification via edge function (model, color, category, features, tags).
  - Review details with editable manual fallback.
  - Date and time pickers with GPS / Network coordinate detection (latitude & longitude) and reverse geocoding.
  - Auto-draft save and recovery in SharedPreferences.

- **Deep Link Navigation**:
  - Direct deep linking with `laf://item/<id>` opening the exact report details directly from notifications, shares, or external links.

- **Smart Keyword Search & Filters**:
  - Full multi-word search powered by Firestore `searchTokens` array lookup.
  - Real-time type filter (`All`, `Lost`, `Found`) and category chips.
  - Automatic pagination (`SEARCH_PAGE_SIZE = 20`) with scroll listener.

- **Intelligent Matching Engine**:
  - Bidirectional matching between published items and existing opposite-type reports.
  - Multi-factor scoring: Category (30%), keyword overlap (25%), primary & secondary colors (20%), location & GPS distance (15-20%), material, and brand.
  - Publisher & candidate match persistence in Firestore.
  - Real-time "Suggested Matches" section on user profile and within report details.
  - Match dismissal and click-through item detail view.

- **Secure Claim & Handover Flow**:
  - Contact requests without exposing phone numbers publicly.
  - Owner approval and rejection workflow.
  - In-app status transitions: `Active` → `Claimed` → `Returned` → `Closed`.

- **Push Notifications (FCM)**:
  - Real-time Firestore notification inbox with unread badge counter.
  - Background system push notifications via `LafFirebaseMessagingService` with notification channel and deep linking.
  - User token synchronization.

- **Content Moderation & Security**:
  - User reporting dialog for abusive or spam listings with private submission to `reports` collection.
  - Hardened Firestore Security Rules (`firestore.rules`) with item owner verification on match notifications and bounded title/message limits.
  - Storage Security Rules (`storage.rules`) preventing unauthorized uploads.
  - AI Edge Function (`supabase/functions/analyze-item`) with secret token authentication and model pinning.

---

## 📐 Architecture & Structure

```
com.laf.lostandfound/
├── activities/
│   ├── ItemDetailActivity.java       # Detail view, actions, claims, matches, more options
│   ├── LoginActivity.java            # Sign-in and email verification flow
│   ├── MainActivity.java             # Main host with bottom navigation and push permissions
│   ├── RegisterActivity.java         # Account creation
│   ├── ReportWizardActivity.java     # 7-step guided report publishing wizard
│   └── SplashActivity.java           # Launcher routing based on auth state
├── adapters/
│   ├── ActivityAdapter.java          # Timeline items
│   ├── CategoryAdapter.java          # Category filter chips
│   ├── ClaimAdapter.java             # Contact requests / claims
│   ├── ItemAdapter.java              # Grid item cards
│   ├── MatchAdapter.java             # Potential match cards with dismiss/open
│   ├── MyPostAdapter.java            # Horizontal list of user's posts
│   └── NotificationAdapter.java      # In-app notifications
├── fragments/
│   ├── HomeFragment.java             # Feed, hero banner, category selector, recent items
│   ├── NotificationsFragment.java    # Notifications list with mark-read/delete
│   ├── ProfileFragment.java          # User profile, stats, my posts, suggested matches, activity
│   └── SearchFragment.java           # Multi-word token search and filtering
├── models/
│   ├── ActivityItem.java             # User activity log
│   ├── Category.java                 # Item category definition
│   ├── Claim.java                    # Contact request / claim
│   ├── Item.java                     # Main item document
│   ├── MatchRecord.java              # Suggested match record
│   ├── Notification.java             # In-app notification item
│   ├── ReportAbuse.java              # Moderation abuse report
│   ├── ReportDraft.java              # Wizard draft persistence
│   ├── SearchResult.java             # Match result with percentage score
│   └── User.java                     # User profile
├── repositories/
│   ├── ActivityRepository.java       # Activity timeline persistence
│   ├── AuthRepository.java           # Firebase Auth & profile operations
│   ├── ClaimRepository.java          # Claim create/read/update
│   ├── ItemRepository.java           # Item publishing, indexing, and search
│   ├── MatchRepository.java          # Match records persistence & listeners
│   ├── NotificationRepository.java   # Notification inbox listener & updates
│   ├── ReportRepository.java         # Moderation report submission
│   └── StorageRepository.java        # Firebase Storage image uploads
├── services/
│   └── LafFirebaseMessagingService.java # Background FCM push notification receiver
└── utils/
    ├── AuthGuard.java                # Auth check helpers
    ├── Constants.java                # App-wide constants, statuses, and categories
    ├── DraftManager.java             # SharedPreferences serialization for wizard drafts
    ├── ImageAnalyzer.java            # HTTP client for AI vision edge function
    ├── ImageLoader.java              # Glide wrapper
    ├── ImageValidationUtils.java     # Image size/format validation & compression
    ├── MatchingEngine.java           # 0-100% scoring engine with GPS Haversine distance
    ├── SearchUtils.java              # Normalization and token index extraction
    ├── TimeUtils.java                # Relative time formatting
    └── ValidationUtils.java          # Input validation helpers
```

---

## 🛠️ Build & Setup Instructions

### Prerequisites
- JDK 17
- Android SDK 34 (minSdk 24, targetSdk 34)
- Firebase Project with Auth, Firestore, Storage, Cloud Messaging, and Cloud Functions enabled.

### 1. Firebase Configuration
1. Place your `google-services.json` inside the `app/` directory.
2. Deploy Firestore Security Rules and Indexes:
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes
   ```
3. Deploy Storage Security Rules:
   ```bash
   firebase deploy --only storage
   ```
4. Deploy Server-Side Cloud Function for FCM Push Delivery:
   ```bash
   firebase deploy --only functions
   ```

### 2. Supabase AI Edge Function
1. Deploy the vision function:
   ```bash
   supabase functions deploy analyze-item --no-verify-jwt
   ```
2. Configure environment variables in Supabase Dashboard:
   - `OLLAMA_API_KEY`: API key for vision provider.
   - `OLLAMA_MODEL`: `gemma3:27b` (default).
   - `AI_CLIENT_SECRET`: Optional secret token validated against client requests.

### 3. Build & Run
Run unit tests:
```bash
./gradlew test
```

Assemble debug APK:
```bash
./gradlew assembleDebug
```

Assemble release APK (requires keystore environment variables):
```bash
export KEYSTORE_PATH=/path/to/keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=your_alias
export KEY_PASSWORD=your_key_password
./gradlew assembleRelease
```
