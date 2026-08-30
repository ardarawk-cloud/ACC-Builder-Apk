DEV SIGNING ONLY

`offgrid-dev.jks` is intentionally a non-production development key for the public OFFGRID prototype build lane.

- Dev application id: `com.offgrid.mesh.dev`
- Purpose: stable update signing across physical-device alpha builds.
- This key MUST NEVER sign the production / Play Store package.
- Production will use a private signing key and a separate release trust boundary.

Because the key is public, APKs signed with it must be treated as development/test builds only.
