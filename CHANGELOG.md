# Changelog

## Unreleased

### Added

- **+ Pair New Device button** next to *Paired Devices*. Opens a live list of nearby named
  devices; pick one to pair and connect. Asks for the scan permission (or Location on
  Android 10/11) itself.
- **`ACTION_PAIR` broadcast** to pair a new device from ADB by `name` or `address`.
  Scans, pairs, then connects (turn this off with `--ez connect false`). The result prints
  in the `am broadcast` output and lists nearby devices when the target isn't found.

### Notes

- Needs `BLUETOOTH_SCAN` on Android 12+. On Android 11 and older it needs
  `ACCESS_FINE_LOCATION`, system Location turned on, and for `ACTION_PAIR`
  `ACCESS_BACKGROUND_LOCATION` (location permissions declared only for those versions).
  `ACTION_PAIR` is restricted to `adb shell`, like the other broadcasts.
- The paired device list now refreshes when a device is paired or unpaired.

## 1.3.1

### Added

- **Mute Button Remap toggle.** A new card with a switch turns the mute remap on or off.
  Off stops the background watcher and returns mute to its normal TV behaviour.

### Notes

- Default is off. Turn the switch on to use the remap.

## 1.3.0

### Added

- **Noise Control card** for Sony headsets (WH-1000XM, WF-1000XM, MDR-). One click cycles
  noise cancelling, ambient sound and off, and the badge shows the last known mode.
- **Mute button remap:** a single mute press toggles noise cancelling and transparency on
  the headset, then restores the TV volume. Needs the Google TV remote's volume control
  set to Chromecast so the key reaches Android.
- **`ToggleTransparencyActivity`**, an invisible activity for Button Mapper on remotes
  where the mute key is mappable.
- **`ACTION_ANC` broadcast** for ADB, Tasker and Home Assistant, with
  `mode=toggle|nc|transparency|ambient|off`.
- Background mute watcher that starts on launch and after boot. One audio state check
  every second, no accessibility service.

### Changed

- Transparency uses the headset's voice focus mode, so speech comes through while other
  ambient noise is reduced.
- The Noise Control card sits at the top of the right column, above the paired devices.
- Version pill shows v1.3.

### Notes

- While the mute watcher is running, mute no longer mutes the TV. It toggles the headset
  instead, and the volume returns to its previous level.
- On a remote whose volume control is set to the TV, the mute key is sent over IR and
  never reaches Android, so the app cannot see it.

## 1.2.0

- Auto-connect on launch.
- Battery indicator on device cards.
- Safe startup volume presets.
- Mobile Quick Settings tile and home screen widget.

## 1.1.0 and earlier

- Initial releases with 1-click connect, ADB connect/disconnect and default device
  support.
