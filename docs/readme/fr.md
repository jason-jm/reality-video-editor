# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · [日本語](ja.md) · [한국어](ko.md) · **Français** · [Deutsch](de.md) · [Español](es.md) · [Русский](ru.md)

**Un logiciel de montage pensé pour le PICO Space Pro.** Enfilez le casque, ouvrez votre premier montage et avancez une coupe de quelques images : toute la scène change de rythme. Des rushes au film terminé, tout reste dans le casque. Ni ordinateur, ni compte, ni connexion Internet à prévoir.

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="Reality Video Editor pour PICO Space Pro, avec le ruisseau dans la fenêtre de montage et le lapin dans celle d’étalonnage">
  </picture>
</a>

[Logiciel libre](../../LICENSE) · [Compiler et installer](../building.md)

## Retrouver la bonne prise

La page Média permet de parcourir les vignettes tout en visionnant le rush sélectionné. Marquez une prise d’un drapeau ou d’un mot-clé et laissez un chutier intelligent rassembler les plans qui correspondent. Il peut même ne montrer que les rushes déjà utilisés, pour retrouver en un coup d’œil ce qui figure au montage.

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Le chutier de Meadow Morning et un aperçu de la prise sélectionnée au bord du ruisseau">
  </picture>
</a>

## De la place pour monter

En affichage ultralarge, le rush et le montage se regardent côte à côte. Repérez le passage à garder, posez-le sur la timeline et relancez la lecture depuis le plan précédent pour juger le raccord. Pour les fichiers lourds, montez avec des fichiers proxy plus légers. L’export repart toujours des originaux.

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="Le moniteur source affiche un ruisseau bordé de fleurs. Le moniteur de montage montre le lapin dans la prairie.">
  </picture>
</a>

## Couper à l’image près

Faites entendre le ruisseau avant de le montrer. L’image et le son se coupent indépendamment ; le montage par propagation décale les plans suivants quand vous raccourcissez une prise. Deux images clés sur l’échelle suffisent pour un lent rapprochement ; une rampe de vitesse ralentit progressivement le mouvement lui-même.

Vous disposez de huit pistes vidéo et huit pistes audio. Pour un tournage multicaméra, synchronisez le son enregistré à part grâce aux formes d’onde, puis changez d’angle directement sur la timeline.

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Plans, sous-titres anglais et deux pistes audio sur la timeline de Meadow Morning">
  </picture>
</a>

## Raccorder deux lumières

Une clairière au soleil, un ruisseau à l’ombre : comment garder l’impression d’un même matin ? Réglez les ombres, les tons moyens et les hautes lumières avec les roues chromatiques. Le moniteur de forme d’onde et la parade RVB permettent de comparer les plans. Gardez une image de référence pour le suivant, ou importez une LUT .cube que vous aimez déjà.

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="Le plan étalonné de la prairie, sa parade RVB et les quatre roues chromatiques">
  </picture>
</a>

## Faire de la place à la voix

Enregistrez votre voix off sur une piste du montage. Éclaircissez une voix sourde à l’égaliseur, faites baisser automatiquement la musique pendant les répliques, puis vérifiez le niveau sonore en LUFS avant l’export. Écoutez une piste en solo pour entendre précisément l’effet d’un réglage.

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="Les formes d’onde de l’ambiance et de la musique sur deux pistes, avec la courbe d’égalisation à quatre points de la piste A1">
  </picture>
</a>

## Un montage, plusieurs livrables

Mettez en file d’attente le film entier, le passage entre deux repères d’entrée et de sortie, ou chaque clip séparément. Exportez en H.264 ou H.265, enregistrez une image fixe ou confiez un WAV à la personne qui s’occupe du son. Les sous-titres peuvent être incrustés ou livrés à part en SRT ou WebVTT. Les résolutions et codecs disponibles dépendent de l’encodeur du casque.

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="Une image du fichier Meadow Morning.mp4 et la file de rendu indiquant la fin de l’export en H.264, en 1080p à 30 images par seconde">
  </picture>
</a>

## Essayer Reality Video Editor

Pour lancer l’application sur votre casque ou un émulateur PICO compatible, suivez le [guide de compilation et d’installation (en anglais)](../building.md). L’icône de globe, dans la bibliothèque de projets comme dans l’éditeur, donne accès aux neuf langues de l’interface. La langue des sous-titres se règle séparément.

Le code et les créations graphiques propres au projet sont placés sous [licence MIT](../../LICENSE). Les dépendances et les crédits figurent dans les [mentions relatives aux contenus tiers](../../THIRD_PARTY_NOTICES.md).

Images de démonstration : *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Des extraits du film ont été remontés pour ces captures.
