# S3: LiteRT-LM Gemma inside our app

**Checked:** 2026-10-02  
**State:** yes. Gemma 4 E2B streams text in our own app on the motorola edge 50 fusion, with the malaria ONNX pack loaded in the same process. Not run on the Nothing A059.

## Question

Can the report narrator, Gemma 4 E2B on LiteRT-LM, run inside `com.deepsight` on the phone, next to an ONNX pack, fast enough for a report? (Issue #3; plan sections 6 and 12.) Gemma decides nothing: it only narrates. Triage stays deterministic (AGENTS.md).

## Setup

- **Phone:** motorola edge 50 fusion: SM7435, Android 15, 7.3 GiB RAM (STATUS.md verified facts). It was in airplane mode with Wi-Fi on during the debug-screen run (status bar).
- **Library:** `com.google.ai.edge.litertlm:litertlm-android:0.17.1` in `:report`. Its POM says Apache-2.0 and pulls in gson 2.14.0, kotlin-reflect 2.4.0 and kotlinx-coroutines-android 1.11.0.
- **Model:** Hugging Face [`litert-community/gemma-4-E2B-it-litert-lm`](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm), file `gemma-4-E2B-it.litertlm`.
  - Size: 2,588,147,712 bytes. sha256: `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
  - Licence `apache-2.0`, ungated (Hugging Face model API). `sha256sum` on the phone matches the LFS hash.
  - Not in the APK; never committed (AGENTS.md).
- **Model location:** `/sdcard/Android/data/com.deepsight/files/gemma-4-E2B-it.litertlm`, which is `Context.getExternalFilesDir(null)`.
- **Code:**
  - `report/.../gemma/GemmaRunner.kt`: load, streaming generate, and LiteRT-LM's own benchmark numbers.
  - `app/src/androidTest/.../GemmaOnDeviceTest.kt`: the device tests.
  - Debug-only `DebugGemmaActivity` (launcher icon "DeepSight Gemma"): the visible app run.
- **Engine settings:** `maxNumTokens` 2048; `cacheDir` = the app's cache dir; a fresh conversation per report; at most 160 output tokens.
- **Prompt:** report-shaped, taken from `contracts/examples/case_result.malaria_thin.json`, with triage `ABNORMAL_FLAG`. It is 94 tokens. Every run stopped by itself after 50–51 tokens.

## Findings

### Minimum Android version

The LiteRT-LM 0.17.1 AAR manifest says minSdk 24, the same as ours. GPU use also needs `<uses-native-library>` entries for `libOpenCL.so` and `libvndksupport.so` (LiteRT-LM Android guide on ai.google.dev). They are declared in `report/src/main/AndroidManifest.xml`, and GPU ran with them.

### Loading from app storage (the push path)

**Verified.** The app process loaded the model from its external files dir.

- The file there was written by the adb shell user (`adb shell cp` from AI Edge Gallery's copy on the same phone): owner `shell:ext_data_rw`, mode `rw-rw----`.
- A probe file sent with `adb push` gets the same owner with mode `rw-rw-rw-`, which is more permissive.
- A full 2.6 GB `adb push` from a laptop was not timed.

### Streaming and timings

| Run | How | Load | First text | Total | Chunks | Prefill tok/s | Decode tok/s |
|---|---|---:|---:|---:|---:|---:|---:|
| GPU, first ever (cold) | device test | 27.3 s | 2.41 s | 8.1 s | 50 | 90 | 7.2 |
| GPU, warm cache | device test | 12.3 s | 1.78 s | 6.4 s | 50 | 186 | 8.7 |
| GPU + MTP, first MTP load | device test | 16.2 s | 2.57 s | 5.0 s | 15 | 77 | 13.4 |
| GPU + MTP + ONNX pack, warm | debug screen, in front | 12.0 s | 1.53 s | 3.7 s | 15 | 255 | 14.8 |
| CPU, first ever (cold) | device test | 51.6 s | 2.48 s | 7.1 s | 49 | 50 | 9.6 |

- **What the columns mean:**
  - Load is the wall clock of `Engine.initialize()`.
  - First text and Total are measured from sending the prompt.
  - Chunks counts the streamed callbacks. With MTP, each callback carries several tokens.
  - The tok/s columns are LiteRT-LM's own numbers (`Conversation.getBenchmarkInfo`).
- **LiteRT-LM's init time is 2.0× our wall clock in all five runs** (54.8/27.3, 24.8/12.3, 32.6/16.2, 24.0/12.0, 103.2/51.6 s), so we report wall clock.
- **Cold vs warm:**
  - The first GPU load writes a 12.6 MB GPU program cache to the cache dir; MTP adds 0.5 MB. Later GPU loads take about 12 s.
  - The first CPU load writes a 788 MB XNNPACK cache to the cache dir.
  - If Android clears the app cache, the next load is cold again.
- **MTP (multi-token prediction, `enableSpeculativeDecoding`):**
  - It speeds up GPU decode from 8.7 to 13.4–14.8 tok/s.
  - For 50 tokens, total time falls from 6.4 s to 3.7–5.0 s.
  - The warm GPU output and the debug-screen MTP output are the same text.
- **CPU:** decode was a little faster than GPU without MTP (9.6 vs 8.7 tok/s), but the first load is 4× slower and leaves the 788 MB cache.
- **Gallery comparison:** AI Edge Gallery's numbers in STATUS.md (decode 10.79 tok/s, prefill 283.6 tok/s, GPU, device UNVERIFIED) were not reproduced exactly in our app.
- **Streaming:** text streamed in every run. In the debug screen, it appeared on screen as it arrived (screenshot checked, not kept).

### Memory with Gemma and an ONNX pack (plan section 12)

`dumpsys meminfo com.deepsight` was taken from inside the instrumentation (via `UiAutomation`) and with adb while the debug screen held both models. MemAvailable comes from `/proc/meminfo`.

| State | TOTAL PSS | Native heap | Graphics | Other mmap | Swap PSS | MemAvailable |
|---|---:|---:|---:|---:|---:|---:|
| malaria ONNX pack only, after a 256-cell batch (device test) | 330 MiB | 192 MiB | 0 | (not logged) | 0.4 MiB | (not logged) |
| + Gemma on GPU, after generating, ONNX run again (device test) | 982 MiB | 442 MiB | 108 MiB | 177 MiB | 182 MiB | 1.46 GiB |
| Gemma GPU+MTP + ONNX pack, both held (debug screen, in front) | 820 MiB | 292 MiB | 117 MiB | 142 MiB | 190 MiB | 1.55 GiB |

- **Raw values:**
  - TOTAL PSS: 337,601 / 1,005,280 / 839,503 kB.
  - MemAvailable: 1,529,084 / 1,630,360 kB.
- **Reading the numbers:**
  - TOTAL PSS includes the swapped-out part (TOTAL RSS was lower: 900,772 and 744,780 kB).
  - "Other mmap" is almost all clean, file-backed pages, which fits the model file being memory-mapped.
- **Effect on other apps:** while Gemma loaded, `lowmemorykiller` killed 3 to 6 cached background apps each time (`oom_score_adj` 920–965, "device is in medium stall and watermark low", once "PSI critical event"). It never killed `com.deepsight`.
- **Verdict:** both models fit on this 7.3 GiB phone, with about 1.5 GiB still available, at the cost of background apps.
  - AI Edge Gallery's allowlist asks for `minDeviceMemoryInGb: 8` for this model, and the model still ran here.
  - A 4–6 GB phone is UNVERIFIED.

### Size and storage

- **APK:** `liblitertlm_jni.so` adds 20.8 MiB, stored uncompressed. The debug APK is about 96 MiB without the local, untracked `breast_breakhis` pack (25.2 MiB), up from 74.1 MiB.
- **Phone storage:** 8.1 GB free before the model, 4.9 GB after the model and the caches (including the 788 MB CPU cache).

### Build: Kotlin version

- **The problem:** `litertlm-android` 0.17.1 and `kotlin-reflect` 2.4.0 are Kotlin 2.4 binaries. With our Kotlin 2.2.10, compiling `:report` fails: "The binary version of its metadata is 2.4.0, expected version is 2.2.0".
- **Workaround:** `-Xskip-metadata-version-check` in `:report` only. `GemmaRunner`'s public API exposes no LiteRT-LM types, so `:app` compiles without it.
- **Side effect:**
  - The app's runtime classpath now resolves kotlin-stdlib 2.4.0; the compile classpath stays 2.2.10.
  - kotlinx-coroutines-android resolves to 1.11.0.
  - The app ran on the phone with these, and the engine and app JVM tests pass (78/78).
- **Proper fix:** upgrade the project to Kotlin 2.4. That changes the shared `libs.versions.toml`, so it is a team decision.

### Output quality (outside S3's question)

Gemma repeated the triage string exactly. Otherwise it mostly restated the facts under a markdown heading and ignored "three sentences". Prompt design, the exact-triage-string check and the template fallback belong to the report work (`docs/architecture.md`, Report).

## Recommendation for the report

- **Backend:** GPU with MTP.
- **Loading:** load once in the background at app start: 12 s warm, about 27 s on the first launch after install. Keep the engine, and use a fresh conversation per case.
- **Cache:** pass `cacheDir`. Without it, every load is cold.
- **CPU:** don't use it on the phone unless GPU fails (52 s first load and a 788 MB cache).
- **Fallback:** keep the template fallback and a time limit, as the architecture says.

## How to reproduce

1. **Get the model on the phone:**
   - Install and open the app once, so its files dir exists.
   - Download the model from the Hugging Face link above.
   - `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/com.deepsight/files/`. On Windows, run this from PowerShell; Git Bash rewrites `/sdcard` paths.
2. **Run the device tests** (don't use `:app:connectedDebugAndroidTest` while the model is on the phone, see the note below):
   - `./gradlew :app:installDebug :app:installDebugAndroidTest` (from `android/`)
   - `adb shell am instrument -w -e class com.deepsight.GemmaOnDeviceTest com.deepsight.test/androidx.test.runner.AndroidJUnitRunner`
   - Read the results with `adb logcat -s DeepSightGemma`.
3. **Run the visible app check:**
   - `adb shell am start -n com.deepsight/.DebugGemmaActivity --es backend gpu --ez mtp true --ez onnx true --ez autorun true`
   - While it holds both models: `adb shell dumpsys meminfo com.deepsight`.

**Note:** `connectedDebugAndroidTest` uninstalls `com.deepsight` after the run, which deletes the 2.6 GB model.
