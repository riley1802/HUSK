# Android Thermal APIs Require Public API Approach

## What went wrong
Initial thermal monitoring implementation used `dumpsys thermalservice` via shell
command to read device thermal sensor data. This returned empty/permission-denied
results because `dumpsys` requires system-level privileges that normal apps don't have.

## Why
`dumpsys thermalservice` is a debug tool intended for ADB shell use by developers
or system apps. It requires `android.permission.DUMP` which is a signature-level
permission — only grantable to apps signed with the platform key.

## How it was fixed
Switched to public Android APIs that require no special permissions:
1. **Battery temperature**: `BatteryManager.EXTRA_TEMPERATURE` via broadcast receiver
   (reports in tenths of a degree, divide by 10 for Celsius). Always available.
2. **Thermal headroom**: `PowerManager.getThermalHeadroom(10)` — returns a float
   where 0.0 = cool and 1.0 = throttling threshold. API 30+ (Android 11).
3. **Thermal status**: `PowerManager.currentThermalStatus` — discrete levels
   (None/Light/Moderate/Severe/Critical/Emergency/Shutdown). API 29+ (Android 10).
4. **Skin/CPU estimation**: Derived from headroom using linear interpolation
   (skin = 25 + headroom * 25, clamped 20-55°C; CPU = skin + 3°C).

## How to prevent
- Always check Android API documentation for permission requirements before
  relying on shell commands or system services
- Prefer public `PowerManager` / `BatteryManager` APIs over `dumpsys` for thermal data
- Public APIs are more reliable across devices and Android versions
