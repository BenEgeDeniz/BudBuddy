# Bud Buddy

[![Latest Release](https://img.shields.io/github/v/release/BenEgeDeniz/BudBuddy?style=flat-square)](https://github.com/BenEgeDeniz/BudBuddy/releases)

<img src="budbuddy.png" alt="Bud Buddy Icon" width="100"/>

*Artwork by [@black_cat_why](https://www.instagram.com/black_cat_why/)*


Bud Buddy is an improved, open-source alternative to the Samsung Galaxy Buds Manager. It brings advanced features to your earbuds to give you full control over your listening experience. Built with Jetpack Compose and modern Android architecture.

## Features

- **Head Gestures:** Control your device with custom head movements.
- **Dynamic Rules:** Auto-adjust your equalizer and noise control based on your currently playing music.
- **Diagnostics & Tracking:** Built-in earbud fit test, wear state monitoring, and "Find My Earbuds".
- **Advanced Controls:** Seamless noise control, sound balance tuning, and a global app search.

## Earbud Support

| Earbud Model | Core Features | Head Gestures |
| :--- | :--- | :--- |
| **Galaxy Buds 4 Pro** | ✅ Supported | ✅ Supported |
| **Galaxy Buds 4** | ✅ Supported | ✅ Supported |
| **Galaxy Buds 3 Pro** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 3** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 2 Pro** | ✅ Supported | ⚠️ Experimental |
| **Galaxy Buds 2** | ✅ Supported | ⚠️ Experimental |

## Building from Source

This project uses standard Android build tools. To build it locally:
1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project using Gradle.

## Download & Installation

You can download the latest stable version of Bud Buddy from our [GitHub Releases](https://github.com/BenEgeDeniz/BudBuddy/releases) page.

If you are feeling adventurous and want to try out the newest unreleased features, you can download the [staging testing builds](https://github.com/BenEgeDeniz/BudBuddy/actions?query=branch%3Astaging) from the Actions tab. These builds are vetted release candidates and are generally safe to use.

If you *really* want to live on the bleeding edge, [development builds](https://github.com/BenEgeDeniz/BudBuddy/actions?query=branch%3Adev) are also available. **Be warned:** these builds can be incredibly unstable and potentially insecure, as anyone can trigger a debug APK build by creating a pull request! Download at your own risk.

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