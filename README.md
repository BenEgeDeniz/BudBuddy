# Bud Buddy

<img src="budbuddy.png" alt="Bud Buddy Icon" width="100"/>

Bud Buddy is an improved, open-source alternative to the Samsung Galaxy Buds Manager. It brings advanced features to your earbuds to give you full control over your listening experience. Built with Jetpack Compose and modern Android architecture.

## Features

- **Custom Head Shake Gestures:** Control your device with intuitive head movements.
- **Dynamic EQ Rules:** Automatically adjust your equalizer based on your environment and the genre of your currently playing music (powered by the Apple Music API).
- **Noise Control Widgets:** Quickly toggle noise cancellation and transparency modes directly from your home screen.
- **Earbud Fit Test:** Ensure you're getting the best seal and sound quality.

## Supported Earbuds

- **Galaxy Buds 4 Pro:** Full support, including Head Gestures.
- **Galaxy Buds 3 Pro:** Supported, but Head Gestures are not available.

## Building from Source

This project uses standard Android build tools. To build it locally:
1. Clone the repository.
2. Open the project in Android Studio.
3. Build the project using Gradle.

## Acknowledgments

The approach to controlling the earbuds via Bluetooth bytes was learned from the fantastic work done in the [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient) project. Huge thanks to the maintainers for their work on the protocol!

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
