# Musifone Power

Low-resource battery watcher for Android 10 (Samsung Galaxy J4 / similar).

## What it does

- Watches battery level with an **event-driven** foreground service (no polling loop)
- Alerts at configurable thresholds (default `40,25,15`) via local notification — once per threshold until you plug in
- Optionally emails your main phone over SMTP **once per discharge**, only during wake hours
- Stores charge/drain samples (~14 days) and estimates when you should recharge during wake hours

## Design notes (battery / RAM)

- Reacts only to `ACTION_BATTERY_CHANGED` and plug/unplug events
- Samples at most once per minute unless the percent or charging state changes
- Quiet low-importance persistent notification while watching
- WorkManager heartbeat every 30 minutes restarts the service if Samsung killed it
- Java + AppCompat Views (no Compose) for a smaller footprint on older devices

## Build

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Install on the J4:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First-run checklist on Samsung

1. Open **Musifone Power** → **Start watching**
2. Tap **Allow unrestricted battery** (or Apps → Musifone Power → Battery → Unrestricted)
3. Disable “Put unused apps to sleep” / “Deep sleeping apps” for this app if present
4. Settings → set thresholds and wake hours
5. Optional: enable email alerts (Gmail SMTP + app password works well)

## Email to your main phone

In Settings:

| Field | Typical Gmail value |
| --- | --- |
| Alert email | your main phone’s mailbox |
| SMTP host | `smtp.gmail.com` |
| Port | `587` |
| Username | full Gmail address |
| Password | [App password](https://myaccount.google.com/apppasswords) |
| From | same as username |

Use **Send test email** before relying on alerts.
