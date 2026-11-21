# Build and install Reality Video Editor

These instructions build a debug APK for an ARM64 PICO device or a compatible PICO OS 6.2 emulator.

## Build the app

1. Install **JDK 21**, the Android SDK platform for **API 37.2**, and Android SDK **Build Tools 37.0.0**.
2. Set `ANDROID_HOME` to the SDK directory, or add `sdk.dir=/path/to/your/android/sdk` to a local `local.properties` file. Connect to the internet for the first build: Gradle fetches AndroidX, Media3 and the PICO Spatial SDK **6.2.8** from the repositories configured in the project.
3. From the repository root, run:

   ```sh
   ./gradlew :app:assembleDebug
   ```

The APK is at `app/build/outputs/apk/debug/app-debug.apk`. On Windows use `gradlew.bat` in place of `./gradlew`. The app targets Android API 37 and requires API 35 or later. Its Android application ID is `com.therealityteam.realityvideoeditor`.

## Install and open

Connect a PICO device with USB debugging enabled and authorize the computer, or start the compatible emulator. With one target connected, run:

```sh
./gradlew :app:installDebug
```

Open **Reality Video Editor** in the headset’s app library. Create a project and import your own video, audio or stills on the Media page. The globe in the project library and the editor opens the language picker. Editing and exporting work offline.

## Run the tests

```sh
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease
```

With a compatible PICO target connected, run `./gradlew :app:connectedDebugAndroidTest`. The device tests cover project save and recovery, media export, the language picker, font coverage and the app name across system languages. See also the [translation and layout checks](localization.md).
