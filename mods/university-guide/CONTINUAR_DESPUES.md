# Punto de reanudación temporal

Última actualización: 13 de octubre de 2026.

## Verificación actual

El 13 de octubre pasaron `clean build runGameTestServer --offline` con Java 21 (2/2 tests)
y el validador de recursos. El JAR conserva el SHA-256 indicado abajo.
Los cambios locales están preservados; el respaldo del workspace se genera mediante
`python ../tools/backup_workspace.py`. Falta la aceptación visual de las pantallas.

## Corrección del 12 de octubre

La pantalla de destinos ajusta las filas por página a la altura disponible, conserva el primer
destino visible al redimensionar y limita el índice de página. Se preservaron los cambios locales
de interfaz, tema y traducciones. `build --offline` con Java 21 y `validate_assets.ps1` pasan.
La aritmética de distribución se comprobó para alturas lógicas de 240, 270, 360, 480 y 720;
falta confirmar visualmente el resultado en el cliente.
JAR de desarrollo: `build/libs/universityguide-1.0.0.jar`.
SHA-256: `E3B8031891AEFCC2449DCA39B52D5E8F19D828E2B019EFC6832F8D1B8C73D02B`.
Los archivos de `releases/` permanecen como registros anteriores. Cambios sin commit.

Este archivo es un checkpoint temporal para continuar el desarrollo sin tener que reconstruir el contexto. El alcance funcional acordado completo está en [PLAN.md](PLAN.md).

## Verificación local del 11 de octubre

- La validación de recursos pasa con 6 archivos JSON y traducciones equivalentes.
- El build limpio con Java 21 finaliza correctamente.
- Se añadieron 2 GameTests: serialización/UUID/texto de la entidad de bloque y ciclo de
  alta, cambio de nombre y baja en `TourStopSavedData`; ambos pasan.
- Los tests y su estructura vacía están excluidos del JAR distribuible.
- Se eliminó el argumento obsoleto `EventBusSubscriber.Bus.MOD`; NeoForge selecciona el bus
  automáticamente según el tipo de evento.
- Candidato local verificado: `releases/universityguide-1.0.0-local-tested.jar`.
- SHA-256: `26C1EDD4F39FC77720ED3B495CB06F7F11B8886AC83C087D07FA4A241FD8E921`.

La versión que estaba en desarrollo en otra computadora ya fue aplicada al worktree actual.
Los cambios resultantes de pantallas, tema visual y traducciones permanecen locales sin commit;
no hay otra importación o fusión pendiente en este momento.

## Estado actual

El proyecto fue creado desde la plantilla oficial de NeoForge para Minecraft Java 1.21.1 y ya contiene una primera implementación funcional completa del mod `universityguide`.

### Implementado

- Proyecto Gradle con NeoForge `21.1.250`, Minecraft `1.21.1` y destino Java 21.
- JDK 21 portable en `.jdk21/jdk-21.0.12.1+1`; se agregó al `.gitignore`.
- Metadatos y nombre del mod actualizados a `University Guide`.
- Plan funcional guardado en `PLAN.md`.
- Bloque invisible `universityguide:tour_stop`:
  - Sin colisión.
  - Visible y seleccionable mediante contorno verde únicamente para operadores en creativo.
  - Colocación, edición y destrucción protegidas por permisos.
  - Validación de piso firme y espacio libre para el NPC.
- Entidad de bloque para cada parada:
  - UUID persistente.
  - Nombre de hasta 64 caracteres.
  - Explicación de hasta 4096 caracteres.
  - Pantalla de edición con campo de nombre y editor multilínea.
- Índice persistente por dimensión usando `SavedData`, actualizado al cargar, colocar, editar o quitar paradas.
- Entidad humanoide `universityguide:guide`:
  - Apariencia de jugador y resolución de skin por nombre de cuenta.
  - Skin predeterminada de Minecraft como respaldo.
  - Persistencia, invulnerabilidad y nombre visible.
  - Pathfinding terrestre, paso por escaleras/losas y apertura de puertas de madera.
  - Estados disponible, caminando y esperando.
  - Destinos limitados a 250 bloques.
  - Grupo formado por jugadores cercanos al iniciar.
  - Espera cuando ningún participante está cerca.
  - Cancelación si el grupo abandona la dimensión durante 60 segundos.
  - Reintentos y cancelación cuando no existe ruta.
  - Persistencia del recorrido y sus participantes.
- Menú paginado de destinos ordenados alfabéticamente, mostrando distancia.
- Pantalla desplazable con título y explicación al llegar.
- Payloads cliente/servidor con validaciones de distancia, permisos, límites de texto y estado del NPC.
- Comandos administrativos:
  - `/universityguide guide spawn <skinAccount> <displayName>`
  - `/universityguide guide cancel <nearest|uuid>`
  - `/universityguide guide remove <nearest|uuid>`
  - `/universityguide marker give`
- Traducciones completas en español e inglés.
- Modelos JSON del objeto/bloque y tabla de botín vacía para el guía.

## Verificaciones realizadas

- `compileJava`: correcto después de implementar todas las clases.
- `build`: correcto antes del último ajuste de separación cliente/servidor.
- Primera prueba de servidor dedicado: detectó que los handlers cliente se enlazaban durante el arranque del servidor.
- Se corrigió el enlace para diferir las clases de pantalla hasta ejecutarse realmente en cliente.
- `compileJava` posterior a la corrección: correcto.
- Segunda prueba de servidor dedicado: cargó Minecraft, NeoForge y `University Guide`, generó el mundo y llegó a `Done (15.756s)` sin errores del mod.
- El servidor temporal de prueba fue detenido después de confirmar el arranque.

Los archivos generados por las pruebas están en `run/` y están ignorados por Git. El crash dentro de `run/crash-reports/` corresponde exclusivamente al problema cliente/servidor ya corregido.

## Siguiente punto exacto para continuar

1. Probar visualmente las tres pantallas rediseñadas, incluyendo cambios de tamaño y escala GUI.
2. Revisar `DestinationScreen`: sus siete filas pueden solaparse con el pie en alturas lógicas pequeñas.
3. Ejecutar `validate_assets.ps1`, `clean build` y `runGameTestServer` sobre el worktree actual.
4. Confirmar los seis archivos locales de interfaz/idiomas en un commit enfocado cuando se aprueben.
5. Crear un mundo creativo con trucos habilitados y probar este flujo completo:
   - Ejecutar `/universityguide marker give`.
   - Colocar dos o más paradas y editar nombre/texto con clic derecho.
   - Ejecutar `/universityguide guide spawn Steve Guía UVG`.
   - Interactuar con el guía, elegir un destino y verificar movimiento, espera y pantalla de llegada.
6. Probar una skin válida y un nombre de cuenta inexistente.
7. Probar el recorrido con puertas, losas, escaleras y una ruta deliberadamente bloqueada.
8. Hacer una prueba multijugador con dos participantes.
9. Añadir GameTests para validación de destinos y para la máquina de estados del guía.

## Comandos de desarrollo

El Java global instalado es Java 26 y no puede arrancar esta versión de Gradle. Hasta instalar Java 21 globalmente, ejecutar Gradle con el JDK portable:

```powershell
& '.\.jdk21\jdk-21.0.12.1+1\bin\java.exe' `
  -classpath '.\gradle\wrapper\gradle-wrapper.jar' `
  org.gradle.wrapper.GradleWrapperMain build --no-daemon
```

Para iniciar el cliente, sustituir `build` por `runClient` en el comando anterior. Para el servidor dedicado, usar `runServer`.

El JAR final aparecerá en `build/libs/universityguide-1.0.0.jar`.

## Notas antes de publicar

- El repositorio plantilla `NeoForgeMDKs/MDK-1.21.1-ModDevGradle` quedó configurado como
  `upstream`. Agregar el repositorio propio como `origin` antes de hacer `push`.
- `jdk21.zip` es el archivo descargado del JDK portable. Está ignorado y puede eliminarse para recuperar aproximadamente 196 MB una vez confirmado que `.jdk21/` funciona.
- Revisar visualmente el modelo humanoide con skins de brazos normales y delgados.
- Afinar velocidad, radios y tiempo de reintento después de probarlo en el mapa real del campus.
- Confirmar el comportamiento de un recorrido activo al reiniciar con el chunk de destino descargado.
- Los avisos de API obsoleta actuales no impiden compilar; pueden limpiarse después de las pruebas funcionales.
