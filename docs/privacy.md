---
title: WorkItOut — Privacy Policy
---

# Privacy Policy

_Last updated: 2026-05-06_

WorkItOut ("the app") is an Android application for indoor cycling workouts.
This policy describes what data the app collects, how it's used, and the
controls you have over it.

## Summary

- The app is designed for solo training. Your data is **shown only to you**.
- The app **never reads** your Strava activity data. The Strava integration
  is **upload-only**: it sends `.fit` files we generate locally to your
  Strava account on your request.
- The app **does not feed Strava data into any AI/ML system**. AI workout
  generation uses only your self-reported FTP and goals.
- You can disconnect Strava at any time from the in-app Settings.

## Data the app collects

### On your device
- **Sensor data** captured during workouts: power, cadence, heart rate,
  speed, elapsed time. Stored locally in the app's encrypted database
  and embedded in `.fit` files.
- **Profile information** you enter: FTP (functional threshold power),
  display name, profile photo, paired device identifiers.
- **Strava OAuth tokens** (if you connect Strava): access and refresh
  tokens stored in `EncryptedSharedPreferences` with AES-256 encryption.

### In Firebase (Google)
- **Authentication state**: Firebase Auth account (anonymous, email, or
  Google sign-in). Used to associate your data with your account.
- **Cloud-synced ride history and profile**: Firestore stores ride
  metadata so it survives reinstall. Visible only to your account.
- **Crash reports**: Firebase Crashlytics receives anonymized crash
  diagnostics. Does not include sensor readings or Strava tokens.

### Network calls
- **Strava API** (`api/v3/uploads`) when you tap Upload to Strava.
- **Anthropic Claude API** for AI workout generation. The request
  contains only your FTP and the workout prompt — never Strava data,
  ride history, or personal identifiers.
- **Firebase Cloud Functions** that brokers Strava OAuth so the Strava
  client secret stays server-side.

## How Strava data is handled

This is the most important section for Strava-connected users.

- **We never call any Strava read endpoint.** We do not fetch your
  profile, activities, friends, segments, or any other data from Strava.
- **The only Strava endpoints we use are uploads:** `POST /api/v3/uploads`
  and the matching status poll `GET /api/v3/uploads/{id}`.
- **No Strava data is displayed in the app**, because we never request it
  in the first place. The only Strava-derived value stored in your
  database is the activity ID returned from a successful upload, used
  to generate a "View on Strava" link to your own activity.
- **Strava data is never shared with third parties**, including AI
  services. The Strava integration runs entirely separately from the
  AI workout generator.
- **You can disconnect Strava at any time** from Settings → Strava →
  Disconnect. This deletes the stored OAuth tokens and clears the
  cached athlete name.

## Data retention and deletion

- **Local data** is removed when you uninstall the app or sign out.
- **Cloud-synced data** is removed when you delete your account in
  Settings. Firestore documents tied to your user ID are deleted; the
  Firebase Auth account is deleted.
- **Strava integration**: disconnecting in Settings deletes our copy of
  your tokens. To revoke our app's access at the Strava level, visit
  [strava.com/settings/apps](https://www.strava.com/settings/apps).

## Data we do not collect

- We do not collect your location.
- We do not show ads or run analytics for advertising.
- We do not sell or share your data with third parties beyond the
  service providers listed above (Google Firebase, Anthropic for AI
  generation, Strava for uploads you initiate).

## Children

WorkItOut is not directed at children under 13.

## Changes to this policy

If this policy changes materially, we'll update the "Last updated" date
above and, where appropriate, notify users in-app.

## Contact

Questions or requests? Email **shaydu9@gmail.com**.
