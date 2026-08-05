# Privacy Policy for Bud Buddy

**Effective Date:** August 5, 2026

This privacy policy applies to the **Bud Buddy** app (hereby referred to as "Application") for mobile devices that was created as an open-source project. This service is provided "AS IS".

## Information Collection and Use

Bud Buddy is designed to be privacy-conscious. The Application does not collect, store, or transmit your personal data to our servers. All processing is done locally on your device or via direct requests to designated third-party APIs.

### Permissions Requested

To provide its core functionality, the Application requires the following permissions:

* **Bluetooth (BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN):** Required to connect to and communicate with your supported Galaxy Buds, enabling features like custom head gestures, EQ adjustments, and noise control. The scan permission is strictly used to find your earbuds and is never used to determine your physical location (`neverForLocation`).
* **Notification Access (BIND_NOTIFICATION_LISTENER_SERVICE, POST_NOTIFICATIONS):** Required to read metadata of the currently playing media. This allows the Application to apply Dynamic EQ Rules based on the music you are listening to.
* **Answer Phone Calls (ANSWER_PHONE_CALLS):** Required to allow you to answer incoming phone calls using custom head shake gestures.
* **Query All Packages (QUERY_ALL_PACKAGES):** Used to identify which media application is currently playing music, ensuring that Dynamic EQ rules are applied accurately.
* **Internet (INTERNET):** Required to communicate with the Apple Music API to retrieve genre information for the currently playing track, which powers the Dynamic EQ feature.

## Third-Party Services

The Application utilizes the **Apple Music API** to fetch metadata (such as music genres) for the currently playing track to automatically adjust your equalizer. 
* Please note that this communication only involves sending the name or metadata of the currently playing track to fetch its genre. 
* We encourage you to review the privacy policy of Apple regarding their API usage.

## Data Retention Policy

The Application does not retain any user-provided data on external servers. Settings, custom EQ rules, and preferences are stored locally on your device and are removed when you uninstall the Application or clear its data.

## Children

The Application does not address anyone under the age of 13. The Application does not knowingly collect personally identifiable information from children under 13 years of age. 

## Changes to This Privacy Policy

We may update our Privacy Policy from time to time. Thus, you are advised to review this page periodically for any changes. We will notify you of any changes by posting the new Privacy Policy on this page.

These changes are effective immediately after they are posted on this page.

## Contact Us

If you have any questions or suggestions about our Privacy Policy, do not hesitate to contact us by opening an issue on the project's GitHub repository.
