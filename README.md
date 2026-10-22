<p align="center">
  <img src="icon/favicon-512x512.png" alt="ServerWatcher logo" width="160" />
</p>

<h1 align="center">ServerWatcher</h1>

<p align="center">
  Start, stop and monitor your AWS EC2 servers from your phone — and keep Route 53 DNS in sync automatically.
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white" />
  <img alt="AWS" src="https://img.shields.io/badge/AWS-Cognito%20%C2%B7%20EC2%20%C2%B7%20Route%2053-FF9900?logo=amazonwebservices&logoColor=white" />
  <img alt="Hilt" src="https://img.shields.io/badge/DI-Hilt-2E7D32" />
  <a href="LICENSE"><img alt="License: PolyForm Noncommercial" src="https://img.shields.io/badge/license-PolyForm%20Noncommercial-blue" /></a>
  <a href="https://github.com/tsk0211/ServerWatcher/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/tsk0211/ServerWatcher?include_prereleases" /></a>
</p>

---

## What it does

ServerWatcher is a personal control panel for a handful of EC2 instances. You group instances under
**domains** and **subdomains**, flip one switch to bring a whole environment up or down, and the app
points your DNS at whichever instance is the primary once it is running.

- **One-tap environments** — a master switch per domain starts or stops every instance under it.
- **Live status** — EC2 state (pending / running / stopping / stopped) polled in the background, plus an
  optional HTTP health check per subdomain with a configurable expected status range.
- **Automatic DNS** — when a *primary* instance reaches `running`, its public IP is upserted as an
  `A` record in the linked Route 53 hosted zone.
- **Per-instance environment variables** — keep a set of `KEY=value` pairs per instance (import from
  JSON) and push them to the machine as cloud-init user data, written to `/etc/serverwatcher.env` on
  every boot.
- **Secure sign-in** — Cognito user pool with first-login password change and optional TOTP MFA
  (enrol an authenticator right from Settings); short-lived AWS credentials come from a
  Cognito identity pool, so no long-lived access keys ever live on the device.
- **Encrypted at rest** — tokens and config in `EncryptedSharedPreferences`, app data in a
  SQLCipher-encrypted Room database.

## How it fits together

```mermaid
flowchart LR
    subgraph Phone["Android app"]
        UI["Compose UI<br/>+ ViewModels"] --> UC["Use cases"]
        UC --> DB[("Room + SQLCipher")]
        UC --> AWS["AWS SDK for Kotlin"]
        UC --> HC["Ktor health checks"]
    end

    AWS -- "sign in / refresh" --> UP["Cognito<br/>User Pool"]
    AWS -- "id token → temp creds" --> IP["Cognito<br/>Identity Pool"]
    IP -- "assume role" --> ROLE["IAM role"]
    AWS -- "Describe / Start / Stop / ModifyAttribute" --> EC2["EC2"]
    AWS -- "UPSERT A record" --> R53["Route 53"]
    HC -- "GET /health" --> EC2
```

The `cdk/` folder contains the CDK stack that provisions the user pool, identity pool and IAM role.

### Project layout

| Layer | Location |
|---|---|
| Domain models, repository interfaces, use cases | `app/.../core/domain/` |
| Room + SQLCipher, DAOs, mappers | `app/.../core/data/db/` |
| Cognito token manager, AWS clients, EC2 / Route 53 repositories | `app/.../core/data/aws/` |
| Health checks (Ktor) | `app/.../core/data/health/` |
| Hilt modules | `app/.../core/data/di/` |
| Feature screens + ViewModels | `app/.../feature/` (auth, home, domaindetail, instancedetail, settings, onboarding, splash) |
| Navigation | `app/.../navigation/NavGraph.kt` |
| Theme and design system | `app/.../ui/theme/`, `app/.../ui/design/` |
| Infrastructure as code | `cdk/` |

## Tech stack

Kotlin 2.0 · Jetpack Compose (Material 3) · Hilt · Room + SQLCipher · Kotlin Coroutines / Flow ·
AWS SDK for Kotlin (Cognito, EC2, Route 53) · Ktor client · Navigation Compose · AWS CDK (TypeScript)

## Install

Download the latest signed APK from the [Releases page](https://github.com/tsk0211/ServerWatcher/releases/latest)
(also kept in [`apk/`](apk/) on the `release` branch), verify it with the `.sha256` file, and open it on your
phone. Android will ask you to allow installs from your browser or file manager the first time.

ServerWatcher has **no server of its own**. Each person deploys the small AWS backend into their own
account (step 1 below), so your credentials, instances and DNS never leave your phone and your AWS account.

## Getting started

1. **Deploy the backend** — `cd cdk && npm install && npx cdk deploy`
2. **Tag the EC2 instances** you want to manage with `ManagedBy=ServerWatcher`
3. **Create a Cognito user** for yourself
4. **Build and install** — `./gradlew assembleDebug`
5. **Open the app** and enter the four stack outputs on the onboarding screen

Full walkthrough, including CLI commands: [SETUP.md](SETUP.md).

Requirements: Android Studio with SDK 35, JDK 17, Node.js 18+, and the AWS CLI configured for the account
you deploy into.

## Screenshots

_Screenshots coming soon._

## Status

Early, single-user project under active development.

## Branches and releases

`dev` is where work happens; `release` is the default branch and carries the latest APK. Releases are cut by
tagging `release` — see [docs/RELEASING.md](docs/RELEASING.md).

## License

[PolyForm Noncommercial 1.0.0](LICENSE). You are free to use, study, modify and share ServerWatcher for any
noncommercial purpose (personal use, hobby projects, research, education, charities). You may **not** use it
for commercial purposes, which includes selling it or building it into a product or service you sell. If you
want to use it commercially, please get in touch. This is a source-available license rather than an OSI
open-source one, and this is not legal advice.

## Security notes

- The Cognito app client has no secret (it is a public mobile client); access is controlled by the
  user pool and the IAM role attached to the identity pool.
- Environment variables are pushed to EC2 **user data**, which is readable by anyone who can call
  `ec2:DescribeInstanceAttribute` on the instance. Do not use it for high-value secrets.
