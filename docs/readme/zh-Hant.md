# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · **繁體中文** · [日本語](ja.md) · [한국어](ko.md) · [Français](fr.md) · [Deutsch](de.md) · [Español](es.md) · [Русский](ru.md)

**專為 PICO Space Pro 打造的影片剪輯軟體。** 戴上頭顯，打開粗剪的片段，把下一個鏡頭提早半拍，看看整場戲的節奏如何改變。從匯入素材到輸出成片都在頭顯裡完成，不需要電腦、帳號或網路連線。

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="為 PICO Space Pro 打造的 Reality Video Editor，剪輯視窗顯示溪邊鏡頭，調色視窗顯示草地上的兔子">
  </picture>
</a>

[開放原始碼](../../LICENSE) · [建置與安裝](../building.md)

## 找出想留下的那一個鏡頭

素材頁把縮圖和預覽放在一起。替喜歡的鏡頭立旗標、加關鍵字，智慧素材箱就能依條件自動整理。也可以像圖中這樣，建一個只收錄已用素材的箱子，一眼看出哪些鏡頭已經剪進片中。

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Meadow Morning 的素材庫，以及所選溪邊鏡頭的預覽">
  </picture>
</a>

## 把剪輯桌展開在眼前

切換到超寬版面，原始素材和時間軸預覽就能並排顯示。先在原片上選好想用的段落，再放上時間軸，看看它跟上一個鏡頭接得順不順。遇到大檔案，可以先用輕量的代理檔剪輯；輸出時仍會讀取原始素材。

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="原始素材監看視窗顯示鮮花環繞的小溪，時間軸監看視窗顯示草地上的兔子">
  </picture>
</a>

## 找到該切換鏡頭的那一格

讓觀眾先聽到溪水，再看見溪流。畫面與聲音可以分開修剪；縮短其中一段時，可以讓後面的片段一起往前移。設定兩個縮放關鍵影格，就能做出緩緩推近的效果；想讓動作本身變慢，可以用變速曲線逐漸放慢播放速度。

時間軸最多容納八條視訊軌和八條音軌。多機拍攝時，還可以依波形同步另外錄製的聲音，在時間軸上切換機位。

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Meadow Morning 的多軌時間軸上排列著影片片段、英文字幕和兩條音軌">
  </picture>
</a>

## 讓兩個鏡頭接成同一場戲

陽光下的草地接到樹蔭裡的小溪，光線可能差了一大截。用色輪分別調整陰影、中間調和亮部，再看波形圖與 RGB 分量圖，找出兩個鏡頭的落差。存一張參考靜格和下一個鏡頭比對，也能匯入自己慣用的 .cube LUT。

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="調色後的草地畫面、該影格的 RGB 分量示波圖，以及四個基礎調色色輪">
  </picture>
</a>

## 替人聲留一點空間

直接在頭顯上把旁白錄進音軌。用等化器改善混濁的人聲，讓配樂在說話時自動降低。想確認調整的效果時，可以單獨監聽一條音軌，輸出前再用 LUFS 錶檢查整段響度。

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="森林環境音與配樂分列兩條音軌，A1 音軌的等化曲線上有四個控制點">
  </picture>
</a>

## 一條時間軸，輸出多種版本

將整部影片、入點到出點之間的一段，或個別片段加入算圖佇列。可以輸出 H.264 或 H.265 影片、擷取一張靜格，或把 WAV 交給負責聲音的夥伴。字幕既能嵌入畫面，也能另外存為 SRT 或 WebVTT 字幕檔。可用的解析度與編碼格式取決於頭顯的編碼器。

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="成片 Meadow Morning.mp4 的一個影格，以及算圖佇列中已完成的 H.264、1080p、每秒 30 格匯出紀錄">
  </picture>
</a>

## 開始使用 Reality Video Editor

若要在頭顯或相容的 PICO 模擬器上執行，請參閱[建置與安裝指南（英文）](../building.md)。在專案庫或剪輯畫面點選地球圖示，就能切換九種介面語言；字幕語言可以另外設定。

本專案的原創程式碼與美術素材採用 [MIT 授權條款](../../LICENSE)。相依套件授權與素材出處請見[第三方聲明](../../THIRD_PARTY_NOTICES.md)。

示範影像選自《Big Buck Bunny》，© 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/)（[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)）。截圖中的片段經過剪輯。
