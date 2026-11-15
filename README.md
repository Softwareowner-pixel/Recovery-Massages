# MessageVault — Android starter

MessageVault saves **new, visible app notification text** locally on the Android phone.

## Features
- Android Notification Listener permission (must be enabled by the phone owner).
- Saves notification app name, package name, title/sender, visible text and timestamp.
- Stores records in a Room database in the app's private local storage.
- Shows saved notifications in a simple Android screen.
- No computer, cloud server or external account is needed while using the app.

## Important limitations
- This reads notification content only. It does not read WhatsApp's private database,
  bypass encryption, or import old messages.
- Only text actually shown in notifications can be saved. Hidden previews, muted chats,
  media-only notifications, disappearing content and notifications not posted by an app
  may not be captured. Group notifications can be summaries.
- Enabling Notification Access grants broad visibility into notifications. Only enable it
  on your own phone or with the phone owner's informed permission.
- This starter captures visible text from non-MessageVault notifications; app-specific
  filtering and export controls can be added in a later version.

## Build with GitHub Actions
1. Upload the full contents of this project to the root of your GitHub repository.
2. Open the repository's **Actions** tab.
3. Select **Build Android APK** and press **Run workflow**, or push a commit to `main`.
4. Open the completed workflow run.
5. In **Artifacts**, download `MessageVault-debug-apk`. Extract it to get `app-debug.apk`.

## Build with Android Studio
Open this folder in a recent Android Studio version, use JDK 17, allow Gradle sync,
then select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
