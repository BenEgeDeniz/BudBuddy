# Bud Buddy

[![Latest Release](https://img.shields.io/github/v/release/BenEgeDeniz/BudBuddy?style=flat-square)](https://github.com/BenEgeDeniz/BudBuddy/releases)

<img src="budbuddy.png" alt="Bud Buddy Icon" width="100"/>

*Artwork by [@black_cat_why](https://www.instagram.com/black_cat_why/)*


Bud Buddy is an improved, open-source alternative to the Samsung Galaxy Buds Manager. It brings advanced features to your earbuds to give you full control over your listening experience. Built with Jetpack Compose and modern Android architecture.

## Features

- **Head Gestures:** Control your device with custom head movements.
- **Dynamic Rules:** Auto-adjust your equalizer and noise control based on your currently playing music.
- **Diagnostics & Tracking:** Built-in earbud fit test, wear state monitoring, and "Find My Earbuds".
- **Advanced Controls:** Seamless noise control (with Quick Settings tile integration), sound balance tuning, and a global app search.

## Requirements

- **Android 10** or newer.
- A compatible pair of Galaxy Buds (see below).

## Earbud Support

| Earbud Model | Core Features | Head Gestures |
| :--- | :--- | :--- |
| **Galaxy Buds 4 Pro** | ✅ Supported | ✅ Supported |
| **Galaxy Buds 4** | ✅ Supported | ✅ Supported |
| **Galaxy Buds 3 Pro** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 3** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 3 FE** | ✅ Supported | ❌ Not Supported |
| **Galaxy Buds 2 Pro** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 2** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds FE** | ✅ Supported | ❌ Not Supported |

## Building from Source

This project uses standard Android build tools. To build it locally:
1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project using Gradle.

## Download & Installation

### 🧪 Google Play Closed Beta
Bud Buddy is now live in **Closed Testing** on Google Play! To join the beta test team:
1. Join the [Bud Buddy Tester Google Group](https://groups.google.com/g/budbuddy-closed).
2. Opt-in to testing via the [Google Play Web Opt-in Link](https://play.google.com/apps/testing/com.benegedeniz.budsdynamiceq).
3. Download or update the app directly from the [Google Play Store](https://play.google.com/store/apps/details?id=com.benegedeniz.budsdynamiceq).

### 📦 GitHub Releases & Builds
You can also download standalone APKs directly:
- **Stable Releases:** Download from our [GitHub Releases](https://github.com/BenEgeDeniz/BudBuddy/releases) page.
- **Staging Builds (Unreleased candidate features):** Download from [Staging Actions Builds](https://github.com/BenEgeDeniz/BudBuddy/actions?query=branch%3Astaging).
- **Bleeding-edge Development Builds:** Download from [Dev Actions Builds](https://github.com/BenEgeDeniz/BudBuddy/actions?query=branch%3Adev). *(Note: dev builds may be unstable).*

## Contributing

We welcome contributions from the community! 

If you'd like to contribute, please read our [Contributing Guidelines](CONTRIBUTING.md) first. It contains important information about our strictly enforced branching strategy (`dev` -> `staging` -> `master`), code quality standards, and PR requirements.

- **Bug Reports & Feature Requests:** Please use the provided [Issue Templates](.github/ISSUE_TEMPLATE) when opening a new issue.
- **Security:** If you find a security vulnerability, please refer to our [Security Policy](SECURITY.md) for reporting instructions.
- **Community:** We expect all participants to adhere to our [Code of Conduct](CODE_OF_CONDUCT.md).

## Acknowledgments

The approach to controlling the earbuds via Bluetooth bytes was learned from the fantastic work done in the [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient) project. Huge thanks to the maintainers for their work on the protocol!

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).