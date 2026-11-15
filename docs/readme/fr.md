# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · [日本語](ja.md) · [한국어](ko.md) · **Français** · [Deutsch](de.md) · [Español](es.md) · [Русский](ru.md)

**Un logiciel de montage hors ligne conçu pour le PICO Space Pro.** Dans un entretien filmé à deux caméras, le bon plan est parfois le regard de la personne qui écoute, juste avant sa réponse. Gardez cette prise dans le moniteur source et écoutez la réplique sur la timeline pour choisir le mot sur lequel couper. Du premier bout à bout à l’étalonnage, au mixage et à l’export, tout se fait dans le casque. Vos rushes restent sur l’appareil et vous pouvez monter sans compte ni connexion Internet.

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="Reality Video Editor pour PICO Space Pro, avec le ruisseau dans la fenêtre de montage et le lapin dans celle d’étalonnage">
  </picture>
</a>

[Logiciel libre](../../LICENSE) · [Compiler et installer](../building.md)

## Retrouver la bonne prise

Au dérushage, la prise sélectionnée se lit à côté des vignettes du chutier. Renseignez la scène, le plan et la prise, marquez vos favoris d’un drapeau ou laissez-vous une note comme « attendre la fin du rire ». Un mot-clé sur les plans de réaction permet de créer un chutier intelligent qui rassemble les bonnes prises encore inutilisées. Dès qu’un plan entre dans le montage, il sort de cette sélection.

Les images de cette page suivent *Meadow Morning*, un montage de 20 secondes. Le chutier contient quatre plans et deux éléments sonores. Ici, le plan du ruisseau est ouvert en prévisualisation.

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Le chutier de Meadow Morning et un aperçu de la prise sélectionnée au bord du ruisseau">
  </picture>
</a>

## De la place pour monter

En affichage ultralarge, le moniteur source et celui du montage se côtoient au-dessus des pistes. Posez un point d’entrée et un point de sortie autour d’une réponse, puis insérez-la pour décaler la suite ou écrasez le passage de même durée. Au clavier, **I** et **O** posent les points. Pour retrouver un regard dans les rushes, placez la tête de lecture dessus et appuyez sur **F** : le moniteur source s’ouvre sur la même image. La molette permet alors d’examiner la suite image par image.

Pour les fichiers caméra lourds, générez des proxys plus légers pour la lecture. L’export repart des originaux.

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="Le moniteur source affiche un ruisseau bordé de fleurs. Le moniteur de montage montre le lapin dans la prairie.">
  </picture>
</a>

## Couper à l’image près

Faites entendre le ruisseau quelques images avant qu’il apparaisse en décalant la coupe son par rapport à la coupe image : c’est un raccord en J. Si la scène traîne, raccourcissez un plan par propagation pour avancer ceux qui suivent. Si le raccord tombe déjà sur le bon temps musical, faites glisser le contenu du plan entre ses deux points de coupe. Vous pouvez ainsi choisir un autre geste de la même prise tout en gardant le rythme.

Vous disposez de huit pistes vidéo et huit pistes audio au maximum. Pour un entretien à deux caméras, synchronisez l’enregistrement séparé avec le son témoin grâce aux formes d’onde, puis changez d’angle à l’image en gardant la voix continue. Deux images clés sur l’échelle suffisent pour un lent rapprochement. Une rampe de vitesse fait entrer progressivement le mouvement dans un ralenti.

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Plans, sous-titres anglais et deux pistes audio sur la timeline de Meadow Morning">
  </picture>
</a>

## Raccorder deux lumières

Comment raccorder une prairie au soleil et un ruisseau à l’ombre ? Étalonnez le premier plan avec les roues primaires, enregistrez une image de référence et comparez-la au suivant en écran partagé. La forme d’onde donne le niveau des ombres et la parade RVB permet de repérer une dominante. Vous pouvez reprendre l’étalonnage de l’image de référence comme point de départ ou importer votre LUT au format .cube.

Pour un visage qui passe dans l’ombre, placez une fenêtre aux contours progressifs autour du visage, suivez son mouvement et relevez l’exposition dans cette zone. Gardez une seconde version de l’étalonnage sur le plan pour essayer un rendu plus chaud.

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="Le plan étalonné de la prairie, sa parade RVB et les quatre roues chromatiques">
  </picture>
</a>

## Faire de la place à la voix

Enregistrez votre voix off directement sur une piste tant que vous avez le rythme de la scène en tête. Écoutez les dialogues en solo, corrigez une voix sourde à l’égaliseur et atténuez les « s » qui sifflent avec le dé-esseur. Faites baisser automatiquement la musique pendant les répliques. En envoyant l’ambiance de forêt et les bruitages vers un même bus, vous pourrez doser l’ensemble avec un seul fader.

À la dernière écoute, le niveau intégré en LUFS vous renseigne sur le volume perçu du film entier. Le niveau de crête vraie indique la marge restante avant l’écrêtage sur le passage le plus fort.

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="Les formes d’onde de l’ambiance et de la musique sur deux pistes, avec la courbe d’égalisation à quatre points de la piste A1">
  </picture>
</a>

## Décliner le film en vertical et préparer les sous-titres

Le film est validé et l’on vous demande une bande-annonce verticale de 15 secondes ? Dupliquez la timeline. Les deux versions partagent le même chutier. Passez la copie en 9:16, recadrez le personnage et raccourcissez l’ouverture pour le public sur téléphone. Le format carré est également disponible.

Importez des sous-titres minutés en SRT ou WebVTT et gardez une piste par langue. Vous pouvez caler chaque version sur le même montage, choisir la piste à incruster dans l’image et livrer un fichier de sous-titres séparé pour chaque langue.

## Un montage, plusieurs livrables

Mettez en file d’attente le film entier, un passage délimité par vos points d’entrée et de sortie pour une projection de travail, ou chaque clip séparément. Exportez une vidéo H.264 ou H.265, une image fixe pour la miniature ou un WAV pour le montage son. Les résolutions et codecs disponibles dépendent de l’encodeur du casque. L’image et l’entrée de la file de rendu ci-dessous correspondent au même export de *Meadow Morning*, en 1080p à 30 images par seconde.

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="Une image du fichier Meadow Morning.mp4 et la file de rendu indiquant la fin de l’export en H.264, en 1080p à 30 images par seconde">
  </picture>
</a>

## Passer le relais avec un montage encore modifiable

Pour reprendre le travail dans un autre logiciel, exportez un FCPXML 1.10 avec le montage des pistes image et son, les marqueurs et les titres simples. Joignez les médias d’origine pour la reconnexion et un export de référence qui montre l’étalonnage et les effets souhaités. Pour une liste de montage plus simple, l’EDL CMX 3600 décrit V1 et les deux premières pistes audio. À l’import, chacun de ces formats crée une nouvelle timeline et retrouve les médias déjà présents dans le chutier d’après leur nom de fichier.

## Essayer Reality Video Editor

Pour lancer l’application sur votre casque ou un émulateur PICO compatible, suivez le [guide de compilation et d’installation (en anglais)](../building.md). L’icône de globe, dans la bibliothèque de projets comme dans l’éditeur, donne accès aux neuf langues de l’interface. La langue des sous-titres se règle séparément.

Le code et les créations graphiques propres au projet sont placés sous [licence MIT](../../LICENSE). Les dépendances et les crédits figurent dans les [mentions relatives aux contenus tiers](../../THIRD_PARTY_NOTICES.md).

Images de démonstration : *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Des extraits du film ont été remontés pour ces captures.
