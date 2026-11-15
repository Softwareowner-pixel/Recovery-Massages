# MessageVault (Android) - WAMR-style deleted message saver

Saves new messages from your chat apps (WhatsApp, WhatsApp Business, Telegram, Signal, Messenger,
SMS ...) on your own phone, shows them like a chat app, and **keeps the original text when the sender
uses "Delete for everyone"**.

## Screens
- **Chats** - one row per conversation, newest first, with search (chat names and message text).
  Tap a chat to read it as chat bubbles. Deleted messages are red with "Deleted by sender".
  Long-press a message to copy or delete it.
- **Deleted** - only the messages that were deleted by the sender. A badge shows how many.
- **Media** - saved photos, videos, GIFs, stickers, voice notes, statuses and documents, with filters.
  Voice notes play inside the app. Files also appear inside the chat they most likely belong to
  (matched by time to the "Photo" / "Voice message" notification - a good guess, not a guarantee).
- Contact / group pictures are taken from the notification.
- **Settings** - notification access, apps to monitor, deleted-message alerts, media saving,
  statuses/documents folder access, PIN app lock, text export, battery settings and cleanup.

## Statuses and documents
Android hides them from the gallery. In Settings tap **Give folder access**, open
*Android > media > com.whatsapp* and tap **Use this folder**. Only new files are saved; a status is on the
phone only after you have viewed it in WhatsApp.

## How it works
- Uses Android's Notification Listener permission, which the phone owner must enable manually.
- Saves each individual message of a chat notification and skips duplicates, group summaries and
  ongoing notifications.
- When the app later sees "This message was deleted" for a message it already saved, it matches the
  two by chat + time, flags the saved message as deleted and (optional) shows an alert with the text.
  If the original was never captured, a "deleted" placeholder is stored instead.
  Language lists are in `DeletedMessages.kt`.
- **Media saving (optional, OFF by default):** copies *new* WhatsApp photos, videos, GIFs, stickers and
  voice notes that WhatsApp itself downloaded into shared storage into the app's private folder.
  Needs the photos/videos/audio permission (Android 14+: choose **Allow all**).

## Limitations (same for every app of this kind)
- Only what appears in a notification can be saved. Hidden previews, muted chats, messages that are
  never shown as a notification, and messages that were already deleted before the app saw them are missed.
- Not captured: view-once media, media you sent yourself, media WhatsApp has not downloaded.
- Messages are stored unencrypted in the app's private storage (backups are disabled). Local only, no cloud.
- Battery savers can stop background apps: set the app to "No restrictions" and allow auto-start.
- Only use this on your own device or with the owner's informed permission.

## Build without a PC (GitHub Actions)
1. Create a **private** GitHub repository and upload everything from this folder
   (including the hidden `.github` folder and `app/debug.keystore`).
2. Open the **Actions** tab. The workflow *Build APK* runs on every push (about 5 minutes).
3. Download the APK from **Releases** (on the main branch) or from the run's **Artifacts**.
4. Install it. Updates install over the old version and keep your saved messages, because the
   repository contains a fixed signing key (`app/debug.keystore`). Keep the repository private.

## Build on a PC
Android Studio with JDK 17, open the folder, then `./gradlew assembleDebug`
-> `app/build/outputs/apk/debug/app-debug.apk`.

## Enable notification access
1. Open the app, go to **Settings**, tap **Open notification access settings** and enable **MessageVault**.
2. If Android shows **"Restricted setting"** (Android 13+, apps installed from an APK):
   **Open App info** > three-dot menu > **Allow restricted settings**, then repeat step 1.
