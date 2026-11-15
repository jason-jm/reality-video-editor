# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · [日本語](ja.md) · [한국어](ko.md) · [Français](fr.md) · **Deutsch** · [Español](es.md) · [Русский](ru.md)

**Ein Videoeditor für die PICO Space Pro, mit dem du offline schneiden kannst.** Bei einem Interview mit zwei Kameras ist die beste Einstellung manchmal der Blick des Gegenübers kurz vor der Antwort. Lass diesen Take im Quellmonitor stehen und hör dir den Satz auf der Timeline an. So findest du das Wort, auf das du schneiden willst. Vom Rohschnitt über Farbkorrektur und Tonmischung bis zum Export bleibst du im selben Projekt auf dem Headset. Dein Material bleibt auf dem Gerät. Zum Schneiden brauchst du weder ein Konto noch eine Internetverbindung.

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="Reality Video Editor für PICO Space Pro mit einer Bachszene im Schnittfenster und dem Kaninchen auf der Wiese in der Farbkorrektur">
  </picture>
</a>

[Open Source](../../LICENSE) · [Bauen und installieren](../building.md)

## Die richtige Aufnahme wiederfinden

Beim Sichten läuft der ausgewählte Take direkt neben den Vorschaubildern im Medienpool. Trag Szene, Einstellung und Take ein, markiere Favoriten mit einer Flagge oder notiere dir „bis nach dem Lachen stehen lassen“. Gib Reaktionsbildern ein Stichwort und lege eine intelligente Ablage für die guten, noch unbenutzten Takes an. Sobald einer davon im Schnitt landet, verschwindet er aus dieser Auswahl.

Die Abbildungen begleiten den 20 Sekunden langen Film *Meadow Morning*. Vier Videoaufnahmen und zwei Tonclips liegen im Medienpool. Hier lässt sich die Aufnahme am Bach sichten, bevor sie auf die Timeline kommt.

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Der Medienpool von Meadow Morning mit einer Vorschau der ausgewählten Aufnahme am Bach">
  </picture>
</a>

## Platz für den ganzen Schnitt

Im ultrabreiten Layout stehen Quellmonitor und Timeline-Monitor nebeneinander über den Spuren. Markiere Anfang und Ende einer Antwort. Beim Einfügen rücken die folgenden Clips nach hinten, beim Überschreiben ersetzt du den gleich langen Abschnitt. Auf der Tastatur setzt du die Marken mit **I** und **O**. Um einen Blick im Original wiederzufinden, stell den Abspielkopf darauf und drück **F**. Match Frame öffnet genau dieses Bild im Quellmonitor. Mit dem Jog-Rad kannst du von dort Bild für Bild weitergehen.

Für große Kameradateien lassen sich kleinere Proxys zur Wiedergabe erzeugen. Der fertige Film wird aus den Originalen gerendert.

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="Der Quellmonitor zeigt einen Bach mit Blumen am Ufer. Im Timeline-Monitor steht das Kaninchen auf der Wiese.">
  </picture>
</a>

## Den richtigen Moment erwischen

Lass das Wasser schon ein paar Bilder plätschern, bevor der Bach zu sehen ist. Für diesen J-Cut setzt du den Tonschnitt früher als den Bildschnitt. Zieht sich die Szene, kannst du sie per Ripple-Trim kürzen und die folgenden Clips nachrücken lassen. Sitzt der Schnitt bereits auf dem richtigen Taktschlag, nimm einen Slip-Trim: Beide Schnittpunkte bleiben an ihrem Platz, während du eine andere Bewegung aus demselben Take auswählst.

Du hast bis zu acht Video- und acht Audiospuren. Bei einem Interview mit zwei Kameras kannst du den extern aufgenommenen Ton über die Wellenform mit dem Kameraton synchronisieren. Wechsle dann nur den Bildwinkel, während der Dialog durchläuft. Für eine langsame Annäherung setzt du zwei Skalierungs-Keyframes. Soll die Bewegung im Bild allmählich langsamer werden, nutze eine Geschwindigkeitsrampe.

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Videoclips, englische Untertitel und zwei Tonspuren auf der Timeline von Meadow Morning">
  </picture>
</a>

## Zwei Einstellungen, eine Lichtstimmung

Von der Wiese in der Sonne geht es zum Bach im Schatten. Gib der ersten Aufnahme mit den primären Farbrädern ihren Look, speichere ein Referenzbild und vergleiche es in der geteilten Ansicht mit der nächsten. Der Wellenformmonitor zeigt, wo die Schatten liegen, die RGB-Parade macht einen Farbstich sichtbar. Du kannst das Grading des Referenzbilds als Ausgangspunkt übernehmen oder eine vertraute .cube-LUT laden.

Bewegt sich ein Gesicht in den Schatten, leg eine Maske mit weichem Rand darüber, lass sie der Bewegung folgen und helle das Gesicht innerhalb der Maske auf. Mit einer zweiten Grading-Version am Clip kannst du auch einen wärmeren Look ausprobieren.

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="Die farbkorrigierte Aufnahme der Wiese mit ihrer RGB-Parade und den vier primären Farbrädern">
  </picture>
</a>

## Der Stimme Raum geben

Sprich einen Kommentar direkt auf eine Audiospur, solange du den Rhythmus der Szene im Ohr hast. Hör den Dialog solo ab, kläre eine dumpfe Stimme mit dem EQ und zähme scharfe S-Laute mit dem De-Esser. Die Musik lässt sich unter Sprache automatisch absenken. Schick Waldatmo und Geräusche auf denselben Mischbus, um sie mit einem Fader gemeinsam zu regeln.

Beim letzten Durchhören zeigt dir die integrierte Lautheit in LUFS, wie laut der Beitrag insgesamt wirkt. Der True-Peak-Wert verrät, wie viel Spielraum an der lautesten Stelle bis zur Übersteuerung bleibt.

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="Waldgeräusche und Musik auf zwei Tonspuren sowie die EQ-Kurve mit vier Kontrollpunkten auf Spur A1">
  </picture>
</a>

## Eine Hochkantfassung mit mehrsprachigen Untertiteln schneiden

Der Hauptfilm ist abgenommen, jetzt wird ein 15-Sekunden-Teaser im Hochformat gebraucht. Dupliziere die Timeline: Beide Fassungen greifen auf denselben Medienpool zu. Stell die Kopie auf 9:16, rück die Person im Bild zurecht und kürze den Einstieg für das Publikum am Handy. Ein quadratisches Format steht ebenfalls zur Wahl.

Importiere zeitcodierte Untertitel als SRT oder WebVTT und leg jede Sprache auf eine eigene Untertitelspur. So kannst du alle Fassungen am selben Schnitt prüfen, die einzubrennende Spur auswählen und für jede Sprache eine separate Untertiteldatei liefern.

## Ein Schnitt, mehrere Ausgaben

Schick die ganze Timeline in die Renderliste, markiere mit In und Out nur einen Ausschnitt zur Abnahme oder gib die Clips einzeln aus. Exportiere Video in H.264 oder H.265, ein Standbild als Vorschaubild oder eine WAV-Datei für den Tonschnitt. Welche Bildgrößen und Codecs verfügbar sind, hängt vom Encoder des Headsets ab. Das Einzelbild und der Eintrag in der Renderliste unten stammen vom selben Export: *Meadow Morning* in 1080p mit 30 Bildern pro Sekunde.

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="Ein Einzelbild aus Meadow Morning.mp4 und der abgeschlossene Export als H.264 in 1080p mit 30 Bildern pro Sekunde">
  </picture>
</a>

## Den Schnitt zur Weiterbearbeitung übergeben

Übernimmt jemand anderes den Schnitt, kannst du die Bild- und Tonspuren mit ihren Schnitten, Markern und einfachen Titeln als FCPXML 1.10 weitergeben. Leg die Originalmedien zum Neuverknüpfen bei und schick einen Referenzexport mit, der Farbkorrektur und Effekte zeigt. Für eine einfache Schnittliste enthält eine CMX-3600-EDL die Spuren V1, A1 und A2. Beide Formate lassen sich auch als neue Timeline importieren. Die Zuordnung zum vorhandenen Material im Medienpool erfolgt über die Dateinamen.

## Reality Video Editor ausprobieren

Um das Projekt auf deinem Headset oder einem kompatiblen PICO-Emulator zu starten, folge der [Anleitung zum Bauen und Installieren (Englisch)](../building.md). Über die Weltkugel in der Projektbibliothek oder im Editor wählst du aus neun Oberflächensprachen. Die Sprache deiner Untertitel stellst du unabhängig davon ein.

Der eigene Code und die Grafiken des Projekts stehen unter der [MIT-Lizenz](../../LICENSE). Angaben zu Abhängigkeiten und Bildnachweise findest du in den [Hinweisen zu Drittanbietern](../../THIRD_PARTY_NOTICES.md).

Filmaufnahmen: *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Für die Bildschirmfotos wurden Ausschnitte des Films neu geschnitten.
