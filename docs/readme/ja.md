# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · **日本語** · [한국어](ko.md) · [Français](fr.md) · [Deutsch](de.md) · [Español](es.md) · [Русский](ru.md)

**PICO Space Pro のための、オフラインで使える動画編集アプリです。** 2台のカメラで撮ったインタビューでは、相手が口を開く直前の表情が効くことがあります。そのテイクをソースモニターに出したまま、タイムラインのせりふを聞き、どの言葉で切り返すかを決められます。ラフカットからカラー、整音、書き出しまで、一つのプロジェクトで進められます。素材はヘッドセット内に保存され、編集にアカウントやネット接続は必要ありません。

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="PICO Space Pro 向けの Reality Video Editor。編集画面には小川のカット、カラー画面には草地のウサギが映っている">
  </picture>
</a>

[オープンソース](../../LICENSE) · [ビルドとインストール](../building.md)

## 残したいテイクを見つける

素材のサムネイルを見ながら、隣のモニターでテイクを試写。シーン、ショット、テイク番号を記録し、気に入ったものにはフラグを付けたり、「笑い終わるまで残す」とメモしたりできます。リアクションにキーワードを付けて、OKテイクのうち未使用のものだけを集めるスマートビンを作れば、カットに使うたびに候補が絞られます。

このページの作例は、20秒の短編 *Meadow Morning* です。映像4本、音声2本を読み込んだメディアプールで、小川のテイクを選んでプレビューしています。

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="「Meadow Morning」のメディアプールと、選択中の小川のカットのプレビュー">
  </picture>
</a>

## 目の前に、広い編集スペースを

ウルトラワイド表示ではトラックの上にソースモニターとタイムラインモニターが並びます。残したい発言にイン点とアウト点を打ち、後続のカットを送るなら挿入、同じ尺の映像を差し替えるなら上書き。キーボードでは **I** と **O** で範囲を指定できます。編集済みのカットから元の表情を探すときは、再生ヘッドを合わせて **F**。マッチフレームでソースの同じコマが開くので、ジョグダイヤルでその先を1コマずつ確認できます。

容量の大きなカメラ素材には軽いプロキシを作って再生に使えます。最終書き出しにはオリジナルが使われます。

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="ソースモニターには花が咲く小川、タイムラインモニターには草地のウサギが映っている">
  </picture>
</a>

## 切り替える瞬間を探す

小川が映る少し前から、水音を聞かせる。音と映像の編集点を別々に動かせば、こうしたJカットを作れます。場面が長ければリップルトリムで間を詰め、後続のカットも前へ。音楽の拍に編集点がぴたりと合っているなら、スリップ編集で前後の編集点を保ち、テイクの中から動きのいい部分を選び直せます。

映像と音声はそれぞれ最大8トラック。2台で撮ったインタビューなら、外部レコーダーの音をカメラ音声の波形に合わせ、せりふをつないだまま映像のアングルだけを切り替えられます。寄りたい場面ではスケールに2点のキーフレームを打ってゆっくり寄せたり、速度を段階的に変えてスローモーションに入ったりできます。

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="「Meadow Morning」のタイムラインに並ぶ映像クリップ、英語字幕、2本のオーディオトラック">
  </picture>
</a>

## 違う光のカットを、ひとつのシーンに

日なたの草地から木陰の小川へ。最初のカットをプライマリーホイールで整えてスチルを保存し、次のカットと画面を分割して見比べます。暗部の位置は波形モニター、色かぶりはRGBパレードで確認。スチルのグレードを次のカットの出発点にしたり、使い慣れた .cube LUTを読み込んだりできます。

人物の顔が陰に入ったら、顔に境界をぼかしたウィンドウを置き、動きをトラッキングして内側だけを明るくできます。同じクリップに別のグレードを残しておけば、少し暖色に振った案との見比べもできます。

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="カラー調整後の草地のカットと、そのフレームのRGBパレード、4つのカラーホイール">
  </picture>
</a>

## 声がきちんと届くミックスに

場面の間を覚えているうちに、ナレーションを直接オーディオトラックへ。せりふをソロで聴き、こもりはEQで、耳につくサ行の音はディエッサーで整えます。話し始めたら音楽を自動で下げ、森の環境音と効果音は一つのバスにまとめれば、1本のフェーダーで両方を調整できます。

最後に通して聴きながら、全編の聴感上の音量を示すインテグレーテッドラウドネス（LUFS）と、最も大きい瞬間のクリッピングまでの余裕を示すトゥルーピークを確認します。

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="森の環境音と音楽の波形、およびA1トラックの4点のEQカーブ">
  </picture>
</a>

## 縦型の短縮版を作り、言語ごとの字幕を用意する

本編が決まったところで、スマートフォン向けに15秒の予告も頼まれたら、タイムラインを複製します。2つの版で素材を共有しながら、コピーを9:16に変更。人物の位置を縦画面に合わせ、冒頭を短く編集できます。正方形も選べます。

タイミング付きのSRTやWebVTTを読み込み、言語ごとに字幕トラックを分けて同じ映像に合わせられます。映像に焼き込むトラックを選ぶほか、各言語の字幕を個別のファイルとして渡せます。

## 一本のタイムラインから、必要な形で書き出す

全編をレンダーキューに入れる、イン点からアウト点までの試写用ファイルを作る、各クリップを書き出す、といった納品方法が選べます。H.264やH.265の映像のほか、サムネイル用の静止画や、音声担当に渡すWAVも書き出せます。使える解像度とコーデックはヘッドセットのエンコーダーによって変わります。下にある1コマとキューの完了記録は、いずれも *Meadow Morning* を1080p・30fpsで書き出したときのものです。

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="書き出した「Meadow Morning.mp4」の1フレームと、H.264・1080p・30fpsの書き出し完了を示すレンダーキュー">
  </picture>
</a>

## 続きを編集できる形で引き継ぐ

別の編集者に渡すときは、映像と音声の各トラックの編集、マーカー、基本的なタイトルをFCPXML 1.10で書き出せます。再リンク用の元の素材に、色やエフェクトの仕上がりが分かるリファレンス動画を添えて渡せます。シンプルな編集リストなら、V1と最初の2本の音声トラックを記録するCMX 3600 EDLも選択できます。どちらの形式も、新しいタイムラインとして読み込み、メディアプールの素材とファイル名で照合できます。

## Reality Video Editor を使ってみる

ヘッドセットまたは対応するPICOエミュレーターで動かすには、[ビルドとインストールの手順（英語）](../building.md)をご覧ください。プロジェクトライブラリやエディターの地球儀アイコンから、9種類の表示言語を選べます。字幕の言語は個別に設定できます。

本プロジェクト独自のコードとアートワークは[MITライセンス](../../LICENSE)で公開しています。依存ライブラリや素材のクレジットは[第三者の権利に関する表記](../../THIRD_PARTY_NOTICES.md)をご覧ください。

デモ映像：『Big Buck Bunny』© 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/)（[CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)）。スクリーンショットには作品の一部を編集して使用しています。
