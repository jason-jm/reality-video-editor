# Reality Video Editor

**English** · [简体中文](docs/readme/zh-Hans.md) · [繁體中文](docs/readme/zh-Hant.md) · [日本語](docs/readme/ja.md) · [한국어](docs/readme/ko.md) · [Français](docs/readme/fr.md) · [Deutsch](docs/readme/de.md) · [Español](docs/readme/es.md) · [Русский](docs/readme/ru.md)

**An offline video editor built for PICO Space Pro.** Sometimes the best shot in an interview is the listener’s face a moment before they answer. Keep that take in the source monitor while you find the right word to cut on in the timeline. Take the same project from rough cut through color, sound and final export on the headset. Your footage stays on the device. No account or internet connection is needed to edit.

<a href="docs/images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/cover.webp">
    <img src="docs/images/reality-video-editor.png" alt="Reality Video Editor for PICO Space Pro, with the creek shot on the edit timeline and the meadow shot open for color grading">
  </picture>
</a>

[Open source](LICENSE) · [Build & install](docs/building.md)

## Find the take you want to keep

Review your rushes with the selected take playing beside the media pool. Log the scene, shot and take, flag a favorite, or leave yourself a note like “hold for the laugh.” Tag reaction shots with a keyword and make a smart bin for the ones you’ve marked as good but still haven’t used. As those shots go into the cut, the bin updates itself.

The gallery follows *Meadow Morning*, a 20-second edit. Four video clips and two audio clips share the pool. The creek shot is open for a closer look before it goes on the timeline.

<a href="docs/images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/media.webp">
    <img src="docs/images/showcase/media.webp" alt="The Meadow Morning media pool and a preview of the selected take, By the creek">
  </picture>
</a>

## Room for the whole cut

In the ultrawide layout, the source and timeline monitors sit side by side above the tracks. Mark the start and end of an answer, then insert it to move the later shots along or overwrite the same stretch of timeline. If you’re cutting with a keyboard, **I** and **O** set the marks. Park on a glance in the cut and press **F** for Match Frame. The source opens on that exact frame. Use the jog dial to step through what follows, one frame at a time.

For large camera files, generate lighter proxies for playback. The final render goes back to the originals.

<a href="docs/images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/workspace.webp">
    <img src="docs/images/showcase/workspace.webp" alt="The source monitor shows a flower-lined creek. The timeline monitor shows the rabbit in the meadow.">
  </picture>
</a>

## Find the frame that makes the edit

Let the sound of the creek arrive a few frames before the water comes into view. Trim the sound and picture separately for that J-cut. If the scene runs long, use a ripple trim to bring the following shots forward. When a cut already lands on a musical beat, a slip edit lets you choose a different gesture from the take while keeping both edit points in place.

There’s room for eight video and eight audio tracks. On a two-camera interview, sync the recorder to the camera audio by waveform, then switch just the picture and keep the dialogue continuous. Two scale keyframes give a shot a slow push-in. A speed ramp eases moving footage into slow motion.

<a href="docs/images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/timeline.webp">
    <img src="docs/images/showcase/timeline.webp" alt="Shots, English subtitles and two audio tracks on the Meadow Morning timeline">
  </picture>
</a>

## Make two shots feel like one scene

A meadow in direct sun cuts to a creek in shade. Grade the first shot with the primary wheels, save a still, then use the split view to compare it with the next shot. The waveform shows where the shadows sit and the RGB parade helps you spot a color cast. You can apply the still’s grade as a starting point or bring in a favorite .cube LUT.

For a face drifting out of the light, place a soft-edged window around it, track the movement and lift the exposure inside the window. Keep a second grade version on the clip if you want to try a warmer look.

<a href="docs/images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/color.webp">
    <img src="docs/images/showcase/color.webp" alt="The graded meadow frame with its RGB parade and the four primary color wheels">
  </picture>
</a>

## Give the voice its own space

Record a voiceover onto an audio track while the scene is fresh in your mind. Solo the dialogue, use EQ to clear a muddy voice and a de-esser to soften sharp S sounds. Let the music dip under speech, then bring the forest ambience and other effects onto one mix bus so a single fader can lower them together.

On the last listen, integrated loudness in LUFS tells you how loud the whole piece feels. True peak shows how close the loudest moment comes to clipping.

<a href="docs/images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/audio.webp">
    <img src="docs/images/showcase/audio.webp" alt="Forest ambience and music on two tracks, with a four-point EQ curve on A1">
  </picture>
</a>

## Cut a vertical version and caption it

When the full film is approved and someone asks for a 15-second vertical teaser, duplicate the timeline. Both cuts share the media pool. Give the copy a 9:16 frame, reposition the subject and shorten the opening for someone watching on a phone. A square frame is also available.

Import timed captions from SRT or WebVTT and keep each language on its own subtitle track. You can check each version against the same edit, choose which track to burn into the picture and deliver a separate subtitle file for each language.

## One timeline, several ways out

Send the whole timeline to the render queue, mark an In–Out range for a review clip, or deliver individual clips. Export H.264 or H.265 video, a still frame for a thumbnail, or a WAV for the sound editor. Available video sizes and codecs depend on the headset’s encoder. The frame and queue entry below come from the same 1080p, 30 fps export of *Meadow Morning*.

<a href="docs/images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="docs/images/showcase/narrow/deliver.webp">
    <img src="docs/images/showcase/deliver.webp" alt="A frame from Meadow Morning.mp4 and its completed H.264 export at 1080p and 30 fps">
  </picture>
</a>

## Hand off an editable cut

If another editor is taking over, export FCPXML 1.10 with the picture and audio tracks, markers and basic titles. Send the original media for relinking and a rendered reference to show the intended grade and effects. For a simpler cut list, CMX 3600 EDL carries V1 and the first two audio tracks. You can also import either format as a new timeline, matching clips to files already in the media pool by name.

## Try Reality Video Editor

To run the project on your headset or a compatible PICO emulator, follow the [build and install guide](docs/building.md). Once inside, the globe in the project library or editor lets you choose from nine interface languages. Your choice won’t change the language of your subtitles.

The original code and artwork are available under the [MIT License](LICENSE). See the [third-party notices](THIRD_PARTY_NOTICES.md) for dependencies and credits.

Demo footage: *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Selected scenes were trimmed for the screenshots.
