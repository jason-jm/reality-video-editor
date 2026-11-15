# Reality Video Editor

Reality Video Editor is a multitrack video editor for PICO spatial windows. Import footage, edit a timeline, grade color, mix audio and export on the headset. The app works offline, including all nine interface languages.

Reality Video Editor 是一款运行在 PICO 空间窗口中的多轨视频编辑器。从导入素材、剪辑、调色、混音到导出，整个流程都可以在头显上离线完成，支持九种界面语言。

Use the globe in the project library or editor to follow your system language or choose **简体中文 · 繁體中文 · English · 日本語 · 한국어 · Français · Deutsch · Español · Русский**. The choice is saved on the headset and is independent of subtitle languages. See [translation and layout checks](docs/localization.md).

![The Edit and Color workspaces in Reality Video Editor](docs/images/reality-video-editor.png)

## Editing and export

- Organize video, audio and stills in bins. Edit on a timeline with up to eight video and eight audio tracks, with trimming, transitions, keyframes and subtitles.
- Grade shots with color wheels, curves and LUTs. Record voiceover and mix dialogue and music with EQ, dynamics and loudness metering.
- Queue H.264 or H.265 video exports, save stills, or export WAV audio and subtitles. Available video resolutions and codecs depend on the device's encoder.

## Build from source

Use **JDK 21** and the Android SDK with **Android API 37.2** and **Build Tools 37.0.0**. Set `ANDROID_HOME` to the SDK directory or set `sdk.dir` in a local `local.properties` file. The Gradle wrapper resolves AndroidX, Media3 and the PICO Spatial SDK **6.2.8** from the repositories configured in this project. The first build requires internet access.

```sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Use `gradlew.bat` on Windows. To install the app, connect an ARM64 PICO device or start a compatible PICO OS 6.2 emulator, then run `./gradlew :app:installDebug`. The app targets Android API 37 and requires API 35 or later.

## Run the tests

```sh
./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease
```

With a compatible PICO device or emulator connected, run the instrumentation suite with `./gradlew :app:connectedDebugAndroidTest`. It exercises project save and recovery, media export, the language picker, font coverage and the app name under different system languages.

## License

The project's original code and assets are available under the [MIT License](LICENSE). The Gradle wrapper, PICO icons and SDK, and other dependencies have their own terms. See the [third-party notices](THIRD_PARTY_NOTICES.md).
