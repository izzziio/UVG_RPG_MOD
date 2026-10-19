# Registro de desarrollo y pruebas

Última actualización: 13 de octubre de 2026.

## Verificación del 13 de octubre

- Java 21: `clean build runGameTestServer --offline` exitoso; 2/2 GameTests aprobados.
- Validador: 6 JSON y traducciones consistentes.
- JAR SHA-256: `E3B8031891AEFCC2449DCA39B52D5E8F19D828E2B019EFC6832F8D1B8C73D02B`.
- Log: `../posted-signage/artifacts/logs/university-guide-verification-2026-10-13.log`.
- La aceptación visual de las pantallas sigue pendiente. No se alteraron las releases.

## Verificación del 12 de octubre

- Paginación de destinos adaptada a la altura, con índice limitado y conservación del primer destino visible.
- `build --offline` con Microsoft Java 21.0.12: exitoso.
- `validate_assets.ps1`: 6 JSON y traducciones consistentes.
- Distribución calculada sin superposición con el pie en alturas lógicas 240, 270, 360, 480 y 720.
- Verificación visual en Minecraft pendiente; no se repitieron los GameTests del servidor de este mod.
- JAR `build/libs/universityguide-1.0.0.jar`, SHA-256
  `E3B8031891AEFCC2449DCA39B52D5E8F19D828E2B019EFC6832F8D1B8C73D02B`.

Este documento registra las verificaciones realizadas sobre `University Guide`, los resultados observados y el punto exacto desde el cual continuar. El alcance funcional completo permanece en [PLAN.md](PLAN.md).

## Entorno utilizado

- Minecraft Java `1.21.1`.
- NeoForge `21.1.250`.
- Java portable Eclipse Temurin `21.0.12.1+1-LTS`.
- Mundo integrado de prueba: `New World`.
- Jugador de desarrollo: `Dev`.

## Compilación y empaquetado

### Build completo inicial

- Se ejecutó `build --no-daemon` con el JDK 21 portable.
- Resultado: `BUILD SUCCESSFUL`.
- Se generó `build/libs/universityguide-1.0.0.jar`.
- Gradle indicó `test NO-SOURCE`: todavía no existen pruebas automatizadas.

### Recompilación desde cero

- Se ejecutó `compileJava --rerun-tasks` para comprobar las advertencias reales del código.
- Resultado: compilación correcta.
- Advertencias encontradas:
  - `EventBusSubscriber.bus()` y `EventBusSubscriber.Bus` están obsoletos y marcados para eliminación.
  - `GuideEntity` usa o sobrescribe al menos una API obsoleta.
  - La asignación `url "..."` de `build.gradle` deberá cambiarse a `url = "..."` antes de Gradle 10.

## Prueba del cliente: ejecución 1

Hora aproximada de inicio: 18:13.

### Arranque

- `runClient` inició con Java 21.
- OpenGL 4.6 fue detectado correctamente.
- NeoForge encontró y cargó `University Guide 1.0.0`.
- Los recursos de `universityguide` se cargaron sin errores de modelos o texturas.
- Minecraft llegó correctamente al menú principal.
- No aparecieron errores o excepciones pertenecientes al mod.

### Mundo integrado

- Se creó e inició el mundo `New World`.
- El servidor integrado arrancó correctamente.
- El jugador `Dev` entró al mundo sin errores.
- Se activó el modo creativo.

### Comandos y comportamiento comprobados

- `/universityguide marker give` funcionó y entregó una parada de tour.
- El usuario informó que el funcionamiento observado estaba en orden hasta ese punto.
- Se cambió de creativo a supervivencia y nuevamente a creativo sin errores del mod.
- `/universityguide guide spawn Steve Guia UVG` creó correctamente al NPC.
- UUID del guía creado durante esta prueba: `71005724-6497-4f11-bfc0-f030f6fa1acb`.
- El guía aceptó la selección de un destino.
- La ruta quedó bloqueada por un árbol y el guía canceló el recorrido mostrando el mensaje de que no pudo encontrar una ruta.

## Corrección aplicada durante las pruebas

La cancelación por el árbol era válida, pero reveló que los reintentos de pathfinding ocurrían demasiado rápido.

Se modificó `GuideEntity` para que:

- espere un total de 10 segundos sin progreso antes de cancelar;
- realice como máximo tres recálculos de ruta;
- distribuya los recálculos durante ese intervalo, en lugar de ejecutarlos en ticks consecutivos;
- reinicie el contador de bloqueo cuando el guía vuelve a avanzar;
- reinicie correctamente los reintentos cuando reanuda un recorrido pausado.

Después del cambio se ejecutó nuevamente el build completo.

- Resultado: `BUILD SUCCESSFUL in 18s`.
- El JAR de `build/libs/` quedó actualizado con la corrección.

## Prueba del cliente: ejecución 2

Hora aproximada de inicio: 18:20.

- El cliente recompilado inició correctamente.
- `University Guide 1.0.0` volvió a cargarse sin errores propios.
- Se abrió el mismo mundo `New World`.
- El servidor integrado inició y `Dev` volvió a entrar correctamente.
- El registro mostró una advertencia de UUID duplicado para una entidad vanilla `Wolf`; no pertenece a `universityguide` y no se observó relación con el mod.
- El cliente continúa disponible para completar las pruebas funcionales pendientes.

## Indicador visual del guía

Se añadió un signo de exclamación dorado animado sobre cada guía para facilitar su localización.

Características implementadas:

- siempre orientado hacia la cámara;
- animación suave de flotación, pulso e inclinación;
- iluminación completa para conservar la legibilidad de noche;
- amarillo luminoso `#FFFF55`, fondo oscuro y sombra para mantener contraste;
- capa translúcida visible detrás de obstáculos;
- opacidad completa hasta 64 bloques y desvanecimiento gradual hasta 96 bloques;
- fase de animación diferente para cada guía, evitando que varios indicadores se muevan exactamente igual.

La implementación compiló correctamente y actualizó el JAR. Se inició una nueva ejecución de `runClient`, se entró a `New World` y no aparecieron errores ni excepciones de renderizado. Queda pendiente la valoración visual del tamaño, altura y contraste.

### Ajuste visual posterior

- El amarillo original se cambió a `#FFFF55` para aumentar el brillo.
- La opacidad del halo visible detrás de obstáculos aumentó de `55 %` a `70 %`.
- El cambio compiló correctamente y se generó nuevamente el JAR.

## Observación durante el recorrido

En la prueba manual posterior, el guía avanzó por la ruta, pero se detuvo brevemente de vez en cuando durante el camino. No se reportó un cierre inesperado ni una excepción del mod.

Este comportamiento queda pendiente de diagnóstico. Se deberá comprobar si las pausas coinciden con:

- recálculos internos del pathfinding;
- pequeños obstáculos o cambios de altura;
- el límite de 12 bloques respecto al participante;
- finalización parcial de la ruta antes del siguiente recálculo.

## Pruebas pendientes

### Prioridad inmediata

- Validar visualmente el nuevo indicador animado desde cerca, lejos y detrás de obstáculos.
- Reproducir y medir las pausas intermitentes del guía durante una ruta despejada.
- Crear o reutilizar dos paradas conectadas por un camino despejado.
- Confirmar que el guía completa el recorrido y abre la pantalla de llegada.
- Alejarse más de 12 bloques, comprobar que el guía se detiene y confirmar que reanuda al acercarse.
- Bloquear deliberadamente la ruta y medir que ahora cancele después de aproximadamente 10 segundos y tres recálculos.

### Recorrido y entorno

- Puertas de madera.
- Losas y escaleras.
- Agua, fuego y desniveles peligrosos.
- Ruta cercana al límite de 250 bloques.
- Destino inexistente, retirado o convertido en una posición no transitable.
- Recorrido activo después de guardar y recargar el mundo.
- Destino cuyo chunk no esté cargado.

### Jugadores y persistencia

- Dos o más participantes.
- Pausa cuando todos los participantes se alejan.
- Cancelación después de 60 segundos si todos se desconectan o abandonan la dimensión.
- Persistencia del guía, skin, destino, estado y participantes.
- Persistencia de paradas y reconstrucción de su índice después de recargar.

### Skins y permisos

- Cuenta de Minecraft válida distinta de `Steve`.
- Cuenta inexistente y textura de respaldo.
- Colocación, edición y destrucción de marcadores por un operador creativo.
- Rechazo de esas acciones en supervivencia, sin permisos o sin ser operador.

### Automatización pendiente

- Completado el 11 de octubre: GameTest para serialización de la entidad de bloque, UUID y texto persistente.
- Completado el 11 de octubre: GameTest para altas, cambios de nombre y bajas en `TourStopSavedData`.
- Añadir pruebas para validación de destinos, distancia máxima y paradas inválidas.
- Añadir pruebas para la máquina de estados y los tiempos de reintento del guía.

## Estado del repositorio

- La implementación se captura en un primer commit local estable el 11 de octubre de 2026.
- El repositorio plantilla oficial de NeoForge quedó como remoto `upstream`.
- Antes de publicar se debe agregar el repositorio propio como `origin`.
- La versión que estaba en la otra computadora ya fue aplicada al worktree actual.
- Los cambios aplicados de pantallas, tema y traducciones siguen locales sin commit.

## Punto exacto para continuar

1. Probar las tres pantallas rediseñadas en varios tamaños y escalas GUI.
2. Reproducir o descartar el posible solapamiento de las siete filas de `DestinationScreen`.
3. Probar una ruta corta y completamente despejada.
4. Verificar pausa, reanudación y pantalla de llegada.
5. Repetir con la ruta bloqueada para validar la nueva espera de 10 segundos.
6. Registrar aquí los resultados y confirmar los cambios locales en un commit enfocado.
