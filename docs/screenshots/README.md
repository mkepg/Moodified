# Screenshots

The README's screenshot gallery is wired to files in this folder. Drop the PNGs in with the exact filenames below and they'll render.

**Target device:** any phone the app targets (Android 8.0+). Portrait, 1080×2400 or similar. Dark or light system theme is fine — Moodified renders the same either way.

| File | Frame to capture |
| --- | --- |
| `01-check-in.png` | Check-in home screen with at least one mood logged today, the recent-days row visible, and the "Today's care" card populated. |
| `02-quick-log.png` | The Quick-log two-tap bottom sheet opened, showing the mood picker mid-selection. |
| `03-insight-overview.png` | Insight screen on the Overview tab, showing the weekly stability card + at least one trend visualization. |
| `04-insight-screen.png` | Insight screen on the Screen tab (or Activity / Sleep — whichever looks best), showing a full weekly bar chart. |
| `05-care.png` | Care screen with an active guidance card or a micro-intervention list. |
| `06-profile.png` | Profile screen showing the Tracking row + notifications row. |

**How to capture:** Android Studio's device toolbar → camera icon, or `adb shell screencap -p /sdcard/shot.png && adb pull /sdcard/shot.png`. Trim to the app window (no notification shade).

Once you drop the PNGs in, the README gallery links resolve automatically. No further wiring needed.
