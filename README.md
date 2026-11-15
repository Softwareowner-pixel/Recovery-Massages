# MessageVault (Android starter)

An Android app starter that saves **new, user-visible app notifications** locally on the phone.
It does not read WhatsApp's private database, bypass encryption, or import old messages.

## What it does
- Uses Android's Notification Listener permission, which the phone owner must enable manually.
- Captures notification title/text from apps the user chooses to monitor.
- Saves captured notification text, app package, sender/title and timestamp in a Room database on the device.
- Shows the saved entries in a simple Compose screen.

## Important limitations
- WhatsApp and other apps decide what appears in notifications. Hidden previews, muted notifications,
  disappearing content, media, and messages that do not produce notifications may not be captured.
- Group notifications may combine messages or show only a summary.
- This is a local-only starter: no computer, cloud server, or account sync is included.
- Android may restrict background behavior; test on the target phone.
- Only use this on your own device or with the device owner's informed permission.

## Build
1. Install Android Studio (a recent stable version) and Android SDK.
2. Open this folder in Android Studio.
3. Let Gradle sync. Use JDK 17.
4. Build > Build Bundle(s) / APK(s) > Build APK(s).

## Enable notification access
1. Install and open the app.
2. Tap **Open notification access settings**.
3. Enable **MessageVault** in Android's Notification access screen.
4. Return to MessageVault. New eligible notifications will be saved locally.

## GitHub
Create a new GitHub repository and upload the contents of this folder. Do not upload
`local.properties`, signing keys, or private credentials.
