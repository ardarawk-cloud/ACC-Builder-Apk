# KIN Native Android

Status: ACTIVE / PHASE 0 FOUNDATION

Tagline: **Your Space. Your People.**

KIN is a relationship-first social app. Phase 0 establishes a native Android shell and dedicated ACC APK Builder workflow without mixing KIN source with other applications in this repository.

## Locked MVP navigation

HOME · MOMENT · + · CHAT · ME

## Core product differentiators

- KIN Circles: relationship context such as Work, Family, School, Gaming, Client, Close Friends, and custom circles.
- Private Relationship Notes: private memory notes about how a connection fits into the user's life.
- My Space: customizable personal profile.
- KIN Skins: profile themes with future editor and Remix Skin flow.
- Guestbook: modern profile messages.
- Chronological Home feed.
- Moment posts with audience control.
- Simple private chat.

## Deliberately excluded from MVP

Reels, live streaming, marketplace, dating, long-form video, creator monetization, payments, large communities, business accounts, AI assistant, and ads.

## Build

The dedicated workflow is `.github/workflows/build-kin-native.yml`.

The workflow builds `native/kin` using Java 17, Android API 35 and Gradle 8.9, then uploads `KIN-Phase0-debug.apk` as a GitHub Actions artifact.

Package working name: `com.kin.app`. This is not a final production application-id lock.
