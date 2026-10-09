# NADMO LIVE Android beta (isolated build branch)

This branch is an independent APK build experiment and does not change the existing project's main branch.

- Android native shell source in `nadmo-live/android/`.
- HTTPS destination: `https://live.nadmo.id/`.
- Android WebView provides restricted camera/microphone capture permissions.
- This is a **debug build only**, not a release signed for public distribution.
- The HTTPS backend is not yet deployed: opening the APK will display a retry screen until the web service is available.
- No payments, tip wallet, or paid ticket room is active; never accept transfers claiming otherwise.
- Stream beta backend source is prepared separately, and must be deployed to the intended URL before mobile test.

Use the GitHub Actions artifact `nadmo-live-android-beta-debug` from the NADMO LIVE workflow. Do not merge this branch into main without consciously migrating it into a dedicated NADMO LIVE repository.
