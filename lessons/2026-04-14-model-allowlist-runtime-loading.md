# Model Allowlist: Build-Time vs Runtime Loading

## What went wrong
After adding Whisper and ECAPA-TDNN model entries to `model_allowlists/1_0_11.json`,
the models didn't appear in the app at runtime. Additionally, when they did appear
after fixing the loading, the app crashed with a NullPointerException.

## Why
Two separate issues:

### Issue 1: Allowlist not loaded
The `model_allowlists/*.json` files in the repo are for build-time reference and
release packaging. At runtime, the app loads the allowlist via this precedence:
1. **Test file**: `/data/local/tmp/model_allowlist_test.json` (dev override)
2. **GitHub raw URL**: Fetched from the repository on app start
3. **Cached local file**: Offline fallback from last successful fetch

During development, changes to the JSON file aren't picked up unless you push
the updated file to the device's test path.

### Issue 2: Missing defaultConfig NPE
`ModelManagerViewModel` accesses `allowedModel.defaultConfig.accelerators` (around
line 949) without null checking. Whisper model entries were missing the `defaultConfig`
block entirely, causing a NullPointerException when the model list was processed.

## How it was fixed
1. Push allowlist to device: `adb push model_allowlists/1_0_11.json /data/local/tmp/model_allowlist_test.json`
2. Added `"defaultConfig": {"accelerators": "cpu"}` to all Whisper and ECAPA-TDNN entries

## How to prevent
- Always push the updated allowlist to device after edits
- Every model entry MUST include a `defaultConfig` block with at least `"accelerators"`
- Test new model entries by verifying they appear in the app's model list before
  building features that depend on them
