# Security & Deployment Notes

Lost & Found is a native Android client backed by Firebase (Auth, Firestore,
Storage) and one Supabase Edge Function for AI image analysis. This document
describes how secrets are handled, what must be deployed, and the known
limitations of the current design.

## 1. What is safe to ship in the APK

| Value | Location | Sensitivity |
| --- | --- | --- |
| Firebase client API key, project id, app id | `app/google-services.json` | Public by design. Identity is enforced by Auth + Firestore/Storage rules, not by hiding this key. |
| `Constants.AI_ANALYSIS_URL` | `utils/Constants.java` | Public endpoint URL. |
| Ollama / vision provider API key | Supabase function environment only | **Secret. Never put it in the app, Gradle files, or source.** |

No AI provider key is present in the Android sources. Do not add one to
`Constants`, `local.properties`, `BuildConfig`, or `google-services.json`.

## 2. Backend deployment

### 2.1 Firestore rules and indexes

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

- Rules: `firestore.rules`
- Indexes: `firestore.indexes.json`
- Config: `firebase.json`

Key guarantees enforced by the rules:

- `users/{uid}` is readable/writable only by that user. Phone numbers stay
  private and are never copied onto public item documents.
- `items/{itemId}` is readable by signed-in members; only the owner can update
  or delete a report. New reports must use the client request UUID as the
  document id and must not contain a `userPhone` field.
- `claims/{itemId_claimantId}` contact requests can only be created against a
  real item and its real owner. Only the claimant and the item owner can read
  them. The owner is the only party that can approve/decline; a claimant may
  re-open their own declined request.
- `notifications` can be read/updated only by the recipient. Cross-user
  notifications are rejected unless they are match alerts for a real item or
  are backed by an existing owner/claimant claim pair.
- `activities` are private to the user who generated them.
- `matches` are readable/writable only by the report owner.

The app relies on the composite indexes in `firestore.indexes.json`; deploying
without them makes filtered feeds/search and unread counts fail with
`FAILED_PRECONDITION`.

### 2.2 Storage rules

```bash
firebase deploy --only storage
```

- Report images: `items/{ownerUid}/{imageId}`
- Profile images: `profile_images/{ownerUid}/{imageId}`
- Reads require an authenticated Firebase user.
- Writes and deletes are restricted to `ownerUid`, with size and `image/*`
  content-type limits (10 MB reports, 5 MB avatars).
- All other paths are closed.

The client sets `Content-Type: image/jpeg` on every upload via
`StorageRepository`, which is required by these rules.

### 2.3 Supabase Edge Function (`analyze-item`)

The function holds the vision provider key and forwards the compressed image to
Ollama. The Android app never talks to the provider directly.

```bash
# Set the secret once per project
supabase secrets set OLLAMA_API_KEY=your-provider-key

# Optional overrides
supabase secrets set OLLAMA_BASE_URL=https://ollama.com
supabase secrets set OLLAMA_MODEL=gemma3:27b

# Deploy without Supabase JWT verification because the mobile client does not
# carry a Supabase session. Keep this flag scoped to this function only.
supabase functions deploy analyze-item --no-verify-jwt
```

`supabase/config.toml` already pins `verify_jwt = false` for this function.
The function:

- accepts only `POST` (and `OPTIONS` for CORS),
- caps the base64 image payload at ~10 MB,
- returns normalized JSON under `data`,
- never returns fabricated analysis when the provider fails (the app shows an
  editable failure state instead).

Because the function is unauthenticated, anyone who learns the URL can consume
your provider quota. For production traffic, put Firebase App Check or a
Supabase anon key / API gateway in front of it and tighten the CORS origin
(currently `*`).

## 3. Local secret hygiene

The repository `.gitignore` excludes `.env` files, service-account JSON, and
Supabase local temp files. Recommended checks before committing:

```bash
git grep -nE "(api[_-]?key|secret|token|password)\s*[:=]" -- . ':!SECURITY.md'
git grep -n "sk-" -- .
```

`google-services.json` contains only public client identifiers. If you enable
additional providers or App Check, keep any server credentials in the Firebase
console / CI secret store, never in this repository.

## 4. Privacy model

- Visitors never receive the reporter's phone number. They send a claim; the
  owner explicitly shares contact details only by approving that claim.
- Item documents store a denormalized display name/photo for feeds, but no
  phone number. Legacy documents that still contain `userPhone` are readable by
  signed-in members until migrated; run a one-off migration to remove that
  field from old reports.
- Claim documents are restricted to the two involved users by rules.
- The AI function receives the image bytes and returns tags; it does not persist
  anything. Provider retention is governed by the Ollama account settings.

## 5. Known limitations / next hardening steps

- The `analyze-item` function is public. Add App Check or a gateway before
  exposing it to real traffic.
- `google-services.json` project keys are public; rely on rules, not obscurity.
- Firestore rules cannot hide individual fields. Any legacy `userPhone` values
  in old item documents should be removed by a migration.
- Consider Firebase App Check for Firestore/Storage to reduce abuse from
  extracted client credentials.
- Keep `minifyEnabled false` only while developing; enable R8 and keep the
  Glide/Firebase keep rules when producing a release build.

## 6. Reporting a vulnerability

Do not open a public issue for security problems. Contact the maintainers
privately with reproduction steps and the affected build/commit.
