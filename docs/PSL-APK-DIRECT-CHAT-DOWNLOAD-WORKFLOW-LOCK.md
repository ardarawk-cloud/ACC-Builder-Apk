# PAPA SAUCE LAB — DIRECT CHAT APK DELIVERY WORKFLOW (LOCKED)

Status: **OWNER APPROVED — HARD LOCK**, 2026-11-04.
Scope: All future Papa Sauce Lab Android APK builds and download deliveries via ChatGPT.

## Correct, owner-tested delivery
1. Reuse the **existing successfully built, signed APK**. When fixing a download failure, never rebuild the app merely to change the download link.
2. Make sure the APK bytes are available in the active `/mnt/data` runtime (use the verified existing copy, or retrieve the release workflow artifact and extract the APK).
3. Call **`python_user_visible.exec` in the commentary channel** to create a **new actual APK file** in `/mnt/data`; use Python `shutil.copyfile` or extract the APK from a release ZIP artifact. Do not merely claim the path exists.
4. Verify the created output: `Path(...).is_file()`, nonzero and expected size, `zipfile.ZipFile(...).testzip() is None`, and when available, signed-release identity/certificate verification from GitHub Actions.
5. Include a **direct `sandbox:/mnt/data/<new-file>.apk` download link** only after the visible Python tool has created and validated that exact file. This is the user-approved working path.
6. If an attempt fails, diagnose the failure and change approach. Do not repeatedly re-upload the same inaccessible attachment, send an unverified Library path, or redirect to GitHub as the default. Never promise a working link until confirmed.
7. Always install over the existing canonical V2 package `com.nadmo.papasaucelab`; **never uninstall** or switch signing certificate, because device-local stock data and Owner pairing must survive.

## Evidence
Owner verified the direct `python_user_visible.exec` copy-and-validate workflow after prior sandbox "Library not found", GitHub in-app freezes, and broken attachment links.

## Scope limits
This is a release-delivery workflow standard; it does not authorize changes to business stock or report data. The actual APK build and its signature still require normal verification.
