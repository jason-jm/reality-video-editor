# Reality Video Editor

[English](../../README.md) · [简体中文](zh-Hans.md) · [繁體中文](zh-Hant.md) · [日本語](ja.md) · [한국어](ko.md) · [Français](fr.md) · [Deutsch](de.md) · **Español** · [Русский](ru.md)

**Un editor de vídeo pensado para PICO Space Pro.** Ponte el visor, abre un primer montaje y adelanta un corte unos fotogramas: cambia el ritmo de toda la escena. Desde los archivos originales hasta la copia final, todo se queda en el dispositivo. Puedes terminar una película sin ordenador, sin cuenta y sin conexión a internet.

<a href="../images/reality-video-editor.png">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/cover.webp">
    <img src="../images/reality-video-editor.png" alt="Reality Video Editor para PICO Space Pro con el arroyo en la ventana de montaje y el conejo en la de etalonaje">
  </picture>
</a>

[Código abierto](../../LICENSE) · [Compilación e instalación](../building.md)

## Encuentra la toma que quieres conservar

La página de medios permite recorrer las miniaturas mientras ves la toma seleccionada. Marca una favorita con una bandera o una palabra clave y deja que una carpeta inteligente reúna los clips que coincidan. Incluso puedes crear una que muestre solo las tomas que ya has usado en el montaje.

<a href="../images/showcase/media.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/media.webp">
    <img src="../images/showcase/media.webp" alt="Los medios de Meadow Morning y una vista previa de la toma seleccionada junto al arroyo">
  </picture>
</a>

## Espacio para ver cómo encaja cada plano

En la vista ultrapanorámica puedes ver el material original junto al montaje. Marca el fragmento que quieres conservar, colócalo en la línea de tiempo y vuelve a reproducir el plano anterior para ver cómo encajan. Con archivos pesados puedes montar con proxies más ligeros. Al exportar se usan los originales.

<a href="../images/showcase/workspace.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/workspace.webp">
    <img src="../images/showcase/workspace.webp" alt="El monitor de origen muestra un arroyo rodeado de flores. En el monitor de la línea de tiempo aparece el conejo en el prado.">
  </picture>
</a>

## Elige el instante del corte

Deja que se oiga el arroyo antes de que el agua entre en cuadro. Recorta la imagen y el sonido por separado o desplaza todos los clips que siguen al acortar un plano. Con dos fotogramas clave de escala puedes acercarte poco a poco a un detalle. Con una rampa de velocidad, en cambio, es la propia acción la que entra en cámara lenta.

Tienes hasta ocho pistas de vídeo y ocho de audio. Si grabaste con varias cámaras, puedes sincronizar el sonido externo mediante la forma de onda y cambiar de ángulo en la línea de tiempo.

<a href="../images/showcase/timeline.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/timeline.webp">
    <img src="../images/showcase/timeline.webp" alt="Planos, subtítulos en inglés y dos pistas de audio en la línea de tiempo de Meadow Morning">
  </picture>
</a>

## Dos planos con la misma luz

Un claro al sol y un arroyo a la sombra pueden parecer rodados en días distintos. Ajusta sombras, medios tonos y altas luces con las ruedas de color y compara los planos con el monitor de forma de onda y el desfile RGB. Guarda un fotograma de referencia para el siguiente plano o importa tu LUT favorita en formato .cube.

<a href="../images/showcase/color.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/color.webp">
    <img src="../images/showcase/color.webp" alt="El fotograma del prado tras el ajuste de color, su desfile RGB y las cuatro ruedas de color primarias">
  </picture>
</a>

## Dale sitio a la voz

Graba una voz en off directamente en una pista de audio. Aclara una voz apagada con el ecualizador, baja la música automáticamente mientras alguien habla y comprueba la sonoridad en LUFS antes de exportar. Puedes escuchar cualquier pista por separado para afinar los ajustes.

<a href="../images/showcase/audio.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/audio.webp">
    <img src="../images/showcase/audio.webp" alt="El sonido del bosque y la música en dos pistas con la curva de ecualización de cuatro puntos de la pista A1">
  </picture>
</a>

## Un montaje, varias entregas

Envía a la cola la película entera, el tramo entre tus puntos de entrada y salida o los clips por separado. Exporta vídeo en H.264 o H.265, guarda un fotograma o entrega un WAV para trabajar el sonido. Puedes incrustar los subtítulos en la imagen o guardarlos aparte en SRT o WebVTT. Los códecs y resoluciones disponibles dependen del codificador del visor.

<a href="../images/showcase/deliver.webp">
  <picture>
    <source media="(max-width: 640px)" srcset="../images/showcase/narrow/deliver.webp">
    <img src="../images/showcase/deliver.webp" alt="Un fotograma de Meadow Morning.mp4 y la cola de renderizado con la exportación H.264 finalizada en 1080p a 30 fotogramas por segundo">
  </picture>
</a>

## Prueba Reality Video Editor

Para ejecutar el proyecto en tu visor o en un emulador PICO compatible, sigue la [guía de compilación e instalación (en inglés)](../building.md). El icono del globo en la biblioteca de proyectos y en el editor te permite elegir entre nueve idiomas de interfaz. El idioma de los subtítulos se configura por separado.

El código y el material gráfico originales del proyecto se publican bajo la [licencia MIT](../../LICENSE). Consulta los [avisos de terceros](../../THIRD_PARTY_NOTICES.md) para conocer las licencias de las dependencias y los créditos.

Imágenes de demostración: *Big Buck Bunny*, © 2008 Blender Foundation / [www.bigbuckbunny.org](https://peach.blender.org/) ([CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)). Se han montado fragmentos de la película para estas capturas.
