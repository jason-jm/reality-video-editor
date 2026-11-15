# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · [日本語](ja.md) · [한국어](ko.md) · [Français](fr.md) · [Deutsch](de.md) · **Español** · [Русский](ru.md)

**Un editor de vídeo sin conexión, diseñado para PICO Space Pro.** En una entrevista a dos cámaras, a veces el mejor plano es la mirada de quien escucha justo antes de responder. Deja esa toma en el monitor de origen y escucha el diálogo en la línea de tiempo para decidir en qué palabra cortar. Puedes llevar el mismo proyecto desde el primer montaje hasta el etalonaje, la mezcla y la exportación final en el visor. El material se queda en el dispositivo y para editar no necesitas cuenta ni conexión a internet.

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="Reality Video Editor para PICO Space Pro con el arroyo en la ventana de montaje y el conejo en la de etalonaje">
  </picture>
</a>

[Código abierto](../../LICENSE) · [Compilación e instalación](../building.md)

## Encuentra la toma que quieres conservar

Al revisar el material, la toma seleccionada se reproduce junto a las miniaturas. Anota la escena, el plano y el número de toma, marca las favoritas con una bandera o déjate una nota como «aguantar hasta que termine de reír». Etiqueta los planos de reacción y crea una carpeta inteligente con las tomas buenas que todavía no has usado. A medida que las incorporas al montaje, van saliendo de esa selección.

Las imágenes de esta página siguen el montaje de *Meadow Morning*, un corto de 20 segundos. El proyecto contiene cuatro clips de vídeo y dos de audio. Aquí se ha abierto el plano del arroyo para decidir dónde cortarlo.

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Los medios de Meadow Morning y una vista previa de la toma seleccionada junto al arroyo">
  </picture>
</a>

## Espacio para ver cómo encaja cada plano

La vista ultrapanorámica sitúa los monitores de origen y de montaje sobre las pistas, uno al lado del otro. Marca la entrada y la salida de una respuesta. Insértala para desplazar los planos que vienen después o sobrescribe un tramo de la misma duración. Con teclado, **I** y **O** fijan los puntos. Para encontrar una mirada en el material original, sitúa el cabezal en ese fotograma y pulsa **F**. Se abrirá el mismo fotograma en el monitor de origen y podrás avanzar con la rueda de desplazamiento fotograma a fotograma.

Para reproducir grabaciones pesadas, puedes generar archivos proxy más ligeros. Al exportar, el editor vuelve a los originales.

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="El monitor de origen muestra un arroyo rodeado de flores. En el monitor de la línea de tiempo aparece el conejo en el prado.">
  </picture>
</a>

## Elige el instante del corte

Haz que se oiga el arroyo unos fotogramas antes de que aparezca en pantalla. Basta con adelantar el corte de sonido respecto al de imagen para construir ese corte en J. Si la escena se alarga, acorta la pausa con un recorte de rizo y los planos siguientes se acercarán. Si el corte ya cae en el golpe de música que buscas, desliza el contenido del plano entre sus dos puntos de corte: podrás elegir otro gesto de la misma toma y conservar el ritmo.

Dispones de hasta ocho pistas de vídeo y ocho de audio. En una entrevista a dos cámaras, sincroniza la grabadora externa con el audio de cámara por forma de onda y cambia solo el ángulo de imagen para mantener la voz continua. Con dos fotogramas clave de escala puedes crear un acercamiento lento. Para ralentizar poco a poco el movimiento de la escena, usa una rampa de velocidad.

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Planos, subtítulos en inglés y dos pistas de audio en la línea de tiempo de Meadow Morning">
  </picture>
</a>

## Dos planos con la misma luz

¿Cómo enlazar un prado al sol con un arroyo a la sombra? Ajusta el primer plano con las ruedas primarias, guarda un fotograma de referencia y compáralo con el siguiente en pantalla dividida. La forma de onda te ayuda a situar las sombras y el desfile RGB a detectar una dominante. Puedes partir de la corrección del fotograma guardado o importar una LUT .cube que ya uses.

Si un personaje entra en sombra, coloca una ventana de bordes suaves sobre su rostro, sigue el movimiento y levanta la exposición dentro de esa zona. Guarda otra versión del etalonaje en el mismo clip para probar un acabado más cálido.

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="El fotograma del prado tras el ajuste de color, su desfile RGB y las cuatro ruedas de color primarias">
  </picture>
</a>

## Dale sitio a la voz

Graba la voz en off directamente en una pista mientras tienes presente el ritmo de la escena. Escucha el diálogo en solo, aclara una voz apagada con el ecualizador y suaviza las eses que silban con un de-esser. La música puede bajar automáticamente durante las intervenciones. Envía el ambiente del bosque y los efectos a un mismo bus para ajustar todo ese fondo con un solo fader.

En la última escucha, la sonoridad integrada en LUFS te orienta sobre el volumen percibido del conjunto. El pico verdadero indica cuánto margen queda antes de la saturación en el instante más fuerte.

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="El sonido del bosque y la música en dos pistas con la curva de ecualización de cuatro puntos de la pista A1">
  </picture>
</a>

## Prepara una versión vertical con subtítulos en varios idiomas

El montaje largo ya está aprobado y te piden un avance vertical de 15 segundos. Duplica la línea de tiempo: las dos versiones comparten los medios. Pasa la copia a 9:16, reencuadra al personaje y acorta el arranque para quien la vea en el móvil. También puedes trabajar en formato cuadrado.

Importa subtítulos con sus tiempos desde SRT o WebVTT y guarda cada idioma en una pista propia. Así puedes revisar todas las versiones sobre el mismo montaje, elegir qué pista incrustar en la imagen y entregar un archivo de subtítulos por idioma.

## Un montaje, varias entregas

Envía el montaje entero a la cola de renderizado, delimita un fragmento entre entrada y salida para revisión o exporta cada clip por separado. Puedes entregar vídeo en H.264 o H.265, un fotograma para la miniatura o un WAV para montaje de sonido. Las resoluciones y los códecs disponibles dependen del codificador del visor. El fotograma y la entrada de la cola que aparecen a continuación corresponden a la misma exportación de *Meadow Morning*, en 1080p a 30 fotogramas por segundo.

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="Un fotograma de Meadow Morning.mp4 y la cola de renderizado con la exportación H.264 finalizada en 1080p a 30 fotogramas por segundo">
  </picture>
</a>

## Entrega un montaje que se pueda seguir editando

Si otra persona va a continuar en una sala de edición, el FCPXML 1.10 conserva los cortes de las pistas de vídeo y audio, los marcadores y los títulos básicos. Envía los medios originales para que pueda volver a enlazarlos y una copia de referencia que muestre el etalonaje y los efectos. Para una lista de edición más sencilla, la EDL CMX 3600 recoge V1 y las dos primeras pistas de audio. También puedes importar ambos formatos como una línea de tiempo nueva: los clips se vinculan a los medios del proyecto por el nombre de archivo.

## Prueba Reality Video Editor

Para ejecutar el proyecto en tu visor o en un emulador PICO compatible, sigue la [guía de compilación e instalación (en inglés)](../building.md). El icono del globo en la biblioteca de proyectos y en el editor te permite elegir entre nueve idiomas de interfaz. El idioma de los subtítulos se configura por separado.

El código y el material gráfico originales del proyecto se publican bajo la [licencia MIT](../../LICENSE). Consulta los [avisos de terceros](../../THIRD_PARTY_NOTICES.md) para conocer las licencias de las dependencias y los créditos.

Imágenes de demostración: *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Se han montado fragmentos de la película para estas capturas.
