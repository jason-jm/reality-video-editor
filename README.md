# Reality Video Editor

**English** · [简体中文](docs/readme/zh-Hans.md) · [繁體中文](docs/readme/zh-Hant.md) · [日本語](docs/readme/ja.md) · [한국어](docs/readme/ko.md) · [Français](docs/readme/fr.md) · [Deutsch](docs/readme/de.md) · [Español](docs/readme/es.md) · [Русский](docs/readme/ru.md)

**A video editor built for PICO Space Pro.** Put on the headset, open a rough cut and watch a scene come together in front of you. Your footage, edits and exports stay on the device. You can finish a film without a desktop, an account or an internet connection.

<a href="docs/images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/cover.webp">
    <img src="docs/images/reality-video-editor.png" alt="Reality Video Editor for PICO Space Pro, with the creek shot on the edit timeline and the meadow shot open for color grading">
  </picture>
</a>

[Open source](LICENSE) · [Build & install](docs/building.md)

## Find the take you want to keep

The Media page puts a preview beside your footage. Flag a favorite, give it a keyword, then use a smart bin to pull matching clips together. You can even make one that shows only the shots you’ve already used, so you know what’s made it into the cut.

<a href="docs/images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/media.webp">
    <img src="docs/images/showcase/media.webp" alt="The Meadow Morning media pool and a preview of the selected take, By the creek">
  </picture>
</a>

## Room for the whole cut

Open the ultrawide layout to see your source shot beside the cut. Mark the part you want to keep, drop it onto the timeline and check how it plays against the previous shot. With larger files, cut with lightweight proxies; the final export still uses your originals.

<a href="docs/images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/workspace.webp">
    <img src="docs/images/showcase/workspace.webp" alt="The source monitor shows a flower-lined creek. The timeline monitor shows the rabbit in the meadow.">
  </picture>
</a>

## Find the frame that makes the edit

Let the sound of the creek arrive before you cut to the water. Trim picture and sound independently, or ripple a cut to move everything that follows with it. Set two scale keyframes for a slow push-in, or use a speed ramp to ease the action into slow motion.

There’s room for eight video and eight audio tracks. For a multicamera shoot, sync an external recording by waveform and switch camera angles on the timeline.

<a href="docs/images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/timeline.webp">
    <img src="docs/images/showcase/timeline.webp" alt="Shots, English subtitles and two audio tracks on the Meadow Morning timeline">
  </picture>
</a>

## Make two shots feel like one scene

A sunny clearing and a shady creek can feel like different days. Adjust shadows, midtones and highlights with the color wheels, then check the waveform and RGB parade to see where the shots differ. Save a reference still for the next shot, or bring in a .cube LUT for a look you already love.

<a href="docs/images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/color.webp">
    <img src="docs/images/showcase/color.webp" alt="The graded meadow frame with its RGB parade and the four primary color wheels">
  </picture>
</a>

## Give the voice its own space

Record a voiceover straight onto an audio track. Use EQ to clear up a muddy voice, let the music dip automatically when someone speaks, and check loudness in LUFS before you export. Solo any track to hear exactly what you’re changing.

<a href="docs/images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/audio.webp">
    <img src="docs/images/showcase/audio.webp" alt="Forest ambience and music on two tracks, with a four-point EQ curve on A1">
  </picture>
</a>

## One timeline, several ways out

Queue the whole film, just the stretch between your In and Out marks, or individual clips. Choose H.264 or H.265 video, save a still, or export a WAV for sound work. Burn captions into the picture or hand them over as separate SRT or WebVTT files. Video sizes and codecs depend on the headset’s encoder.

<a href="docs/images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/deliver.webp">
    <img src="docs/images/showcase/deliver.webp" alt="A frame from Meadow Morning.mp4 and its completed H.264 export at 1080p and 30 fps">
  </picture>
</a>

## Try Reality Video Editor

To run the project on your headset or a compatible PICO emulator, follow the [build and install guide](docs/building.md). Once inside, the globe in the project library or editor lets you choose from nine interface languages. Your choice won’t change the language of your subtitles.

The original code and artwork are available under the [MIT License](LICENSE). See the [third-party notices](THIRD_PARTY_NOTICES.md) for dependencies and credits.

Demo footage: *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Selected scenes were trimmed for the screenshots.
