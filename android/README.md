# Crondroid for Android

Schedule your phone to open a link or an app at a set time: for example, open a Twitch
channel in the Twitch app at 9:00 every weekday.

Built for a Pixel 10a, but it works on any phone running Android 10 or newer.

## Install

1. On your phone, open the repository's
   [**latest** release](https://github.com/CassidyPrather/crondroid/releases/tag/latest)
   and tap `crondroid.apk`. (Every push to `main` rebuilds it. Builds from other
   branches are attached to their
   [workflow runs](https://github.com/CassidyPrather/crondroid/actions/workflows/android.yml)
   as the `crondroid-apk` artifact.)
2. When Android asks, allow your browser to install unknown apps, then tap **Install**.
3. Open Crondroid and tap **Allow** on each item in the **Finish setting up** card.
   **Display over other apps** is the important one: without it, a schedule that fires
   while you are using the phone shows up as a notification you have to tap.

Newer builds install over older ones and keep your schedules, because every build is
signed with the same key (`app/sideload.keystore`, committed on purpose). That key is
public, so it is only fit for sideloading.

## Use

Tap **New schedule**, then:

- **Time**: when to fire.
- **Days**: which days to repeat on. Pick none and the schedule runs once, then turns
  itself off.
- **Link to open**: for example `twitch.tv/channelname` (`https://` is added for you).
- **Open with**: *Default app* lets Android decide. Pick **Twitch** to make sure the link
  opens in the app instead of a browser. Leave the link empty and pick an app to just
  open that app.
- **Test now**: runs the schedule right away so you can check it.
- **Audio only** (Twitch channel links only): Crondroid plays the stream's sound itself,
  with no need to unlock the phone. Play, pause and stop controls appear on the lock screen
  and in the notification shade. If the channel isn't live yet, it keeps checking for 15
  minutes. Twitch has no official way to do this, so Crondroid uses the same unofficial
  method as Twitch's website and tools like Streamlink. If Twitch changes it, audio-only
  schedules stop working until the app is updated. Ads in the stream still play.

When a schedule fires with the phone locked, the screen turns on and asks you to unlock,
then opens the link. A small alarm icon in the status bar shows that a schedule is armed.

## How it works

- `AlarmScheduler` arms each schedule with `AlarmManager.setAlarmClock`. That call fires
  at the exact minute, even in Doze.
- `AlarmReceiver` handles the alarm. It starts `FireActivity` directly when Crondroid
  may display over other apps. Otherwise it posts a full-screen notification. Then it
  arms the next repeat.
- `FireActivity` shows over the lock screen, asks you to unlock, and opens the target.
- For audio-only schedules, `AlarmReceiver` starts `AudioService` instead. `Twitch`
  looks up the channel's audio-only HLS playlist, and `AudioService` plays it with
  ExoPlayer as a media-playback foreground service with a media session.
- `RescheduleReceiver` re-arms everything after a reboot, an app update, or a clock or
  time zone change.

## Build

CI builds the app (see `.github/workflows/android.yml`). To build it yourself, install
Android Studio or the Android SDK, then from this directory run:

```sh
gradle testDebugUnitTest assembleRelease   # Gradle 8.14
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.
