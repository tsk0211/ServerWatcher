# ServerWatcher Setup Guide

## Prerequisites
- AWS CLI configured with appropriate credentials
- Node.js 18+ installed
- Android Studio with SDK 35 installed
- Java 17 JDK

---

## Step 1 — Deploy CDK Infrastructure

```bash
cd cdk
npm install
npx cdk bootstrap   # Only needed once per AWS account/region
npx cdk deploy
```

By default the app role may change DNS records in any hosted zone of the account. To limit it to
specific zones, pass their IDs: `npx cdk deploy -c hostedZoneIds=Z0123456789ABC,Z0987654321XYZ`.
If you deployed an earlier version of this stack, run `cdk deploy` again — the role now also allows
Route 53 record updates and pushing user data to tagged instances.

Note the stack outputs printed at the end:
- `ServerWatcher-UserPoolId`
- `ServerWatcher-UserPoolClientId`
- `ServerWatcher-IdentityPoolId`
- `ServerWatcher-Region`

---

## Step 2 — Configure the App

There is nothing to edit in the source. On first launch the app shows an
**onboarding screen** asking for the four stack outputs from Step 1:

| Field | Value |
|---|---|
| User Pool ID | `us-east-1_XXXXXXXXX` |
| Client ID | `XXXXXXXXXXXXXXXXXXXXXXXXXX` |
| Identity Pool ID | `us-east-1:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` |
| Region | `us-east-1` |

They are stored in `EncryptedSharedPreferences` and can be changed later in
**Settings**.

---

## Step 3 — Create a Cognito User

In the AWS Console → Cognito → User Pools → `ServerWatcherUserPool`:
1. Click **Create user**
2. Enter email and temporary password

Or via CLI:
```bash
aws cognito-idp admin-create-user \
  --user-pool-id YOUR_USER_POOL_ID \
  --username your@email.com \
  --temporary-password TempP@ss123 \
  --message-action SUPPRESS
```

The temporary password is fine: on first sign-in the app asks you to choose a permanent
one. You can enable two-factor authentication from **Settings → Two-factor authentication**
(it shows a key to add to your authenticator app). The pool has MFA set to optional.

---

## Step 4 — Tag EC2 Instances

Tag any EC2 instances you want ServerWatcher to manage:

```bash
aws ec2 create-tags \
  --resources i-0123456789abcdef0 \
  --tags Key=ManagedBy,Value=ServerWatcher
```

Or in the AWS Console → EC2 → Instances → Tags tab → Add tag:
- Key: `ManagedBy`
- Value: `ServerWatcher`

---

## Step 5 — Build the APK

```bash
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Step 6 — Install and Open

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or drag the APK to an Android emulator/device.

Open **ServerWatcher** → enter the stack outputs on the onboarding screen → sign in → start managing your EC2 instances.

---

## Architecture Notes

| Layer | Location |
|---|---|
| Domain models & interfaces | `core/domain/` |
| Room DB + SQLCipher | `core/data/db/` |
| AWS SDK (Cognito + EC2) | `core/data/aws/` |
| Ktor health checks | `core/data/health/` |
| Hilt DI modules | `core/data/di/` |
| ViewModels | `feature/*/` |
| Compose screens | `feature/*/` |
| Navigation | `navigation/NavGraph.kt` |
| Design system | `ui/theme/`, `ui/design/` |
