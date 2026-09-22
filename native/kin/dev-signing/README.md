# KIN development signing

This folder contains a DEVELOPMENT-ONLY signing key encoded as base64.

Purpose: every GitHub Actions development APK uses the same certificate, so new KIN dev builds can update the previously installed dev build instead of requiring uninstall/reinstall.

Development package: `com.ardacore.kin.dev`

This key is intentionally not a production credential and must never be used for the Google Play release package `com.ardacore.kin`.

Production releases must use Google Play App Signing plus a separate private upload key stored only in GitHub Actions secrets / secure key storage.
