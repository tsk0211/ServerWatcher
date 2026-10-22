# Releasing ServerWatcher

## Branches

| Branch | Purpose |
|---|---|
| `dev` | Day-to-day work. Push straight to it. CI builds and tests every push. |
| `release` | What people see by default. Receives `dev` when you want to publish. Holds the latest signed APK in `apk/`. |

Flow: **push to `dev` → merge `dev` into `release` → tag `release` → CI publishes.**

## One-time setup: the signing key

An APK must be signed, and every future update has to be signed with the **same key**. Lose it and
installed copies can no longer be updated, so back it up somewhere safe (password manager, encrypted
drive) and **never commit it**. `*.jks` and `*.keystore` are git-ignored.

1. Create the key (you will be prompted for passwords — choose strong ones and keep them):

   ```bash
   keytool -genkeypair -v -keystore serverwatcher-release.jks -alias serverwatcher \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Store it as repository secrets (GitHub → Settings → Secrets and variables → Actions), or with the
   GitHub CLI:

   ```bash
   # macOS / Linux
   base64 -w0 serverwatcher-release.jks | gh secret set KEYSTORE_BASE64
   # Windows PowerShell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("serverwatcher-release.jks")) | gh secret set KEYSTORE_BASE64

   gh secret set KEYSTORE_PASSWORD     # the keystore password
   gh secret set KEY_ALIAS             # serverwatcher
   gh secret set KEY_PASSWORD          # the key password
   ```

## Publishing a version

```bash
# 1. Work lands on dev
git push origin dev

# 2. Promote dev to release (a merge commit is fine; release may be ahead by APK commits)
git switch release
git pull
git merge dev
git push origin release

# 3. Tag the release commit — this triggers .github/workflows/release.yml
git tag v0.1.0
git push origin v0.1.0
```

The workflow then:

1. checks the tag is on `release`,
2. runs the unit tests and builds a **signed** release APK (`versionName` = the tag,
   `versionCode` = the workflow run number),
3. verifies the signature with `apksigner`,
4. creates the GitHub Release with the APK and its SHA-256 checksum (versions `0.x` and anything
   with a `-` suffix are marked *pre-release*),
5. commits the APK to `apk/ServerWatcher-latest.apk` on the `release` branch.

Use [semantic versioning](https://semver.org): `v0.2.0` for features, `v0.2.1` for fixes, `v1.0.0`
once you are happy to call it stable.

## Verifying a download

```bash
sha256sum -c ServerWatcher-0.1.0.apk.sha256
```

## Building a release locally

Without the signing variables the release build is produced **unsigned**:

```bash
./gradlew assembleRelease -PversionName=0.1.0 -PversionCode=1
```

To sign locally, export `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` first.

## Notes

- Release builds are **not minified** yet. R8 with the AWS SDK, Ktor and SQLCipher needs testing on a
  device before it is switched on; until then the APK is larger but behaves like the debug build.
- Each release adds one APK to the `release` branch history. Older versions stay available as GitHub
  Release assets either way.
