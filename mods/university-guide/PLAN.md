# Plan de implementación: guía turístico universitario

## Resumen

Crear desde cero un mod para Minecraft Java 1.21.1 con NeoForge y Java 21. Incluirá un NPC humanoide que abre un menú de destinos, calcula una ruta terrestre hasta una parada invisible y muestra su explicación al llegar.

Cada NPC atenderá un recorrido a la vez. Podrán existir varios guías independientes, colocados exclusivamente por operadores mediante comandos.

## Implementación

- Crear el proyecto Gradle/NeoForge con mod ID `universityguide`, recursos en español y configuración para cliente, servidor dedicado y GameTests.
- Implementar `GuideEntity` como criatura persistente, invulnerable y sin aparición natural:
  - Estados sincronizados: disponible, caminando y esperando.
  - Caminata por suelo, escaleras y losas; podrá abrir puertas de madera.
  - Evitará agua, fuego y caídas; no romperá bloques, usará portales, escalará ni se teletransportará.
  - Mantendrá la skin indicada mediante una cuenta de Minecraft y utilizará una textura local genérica si no puede resolverla.
- Comandos administrativos, limitados a operadores:
  - `/universityguide guide spawn <skinAccount> <displayName>`: crea un guía.
  - `/universityguide guide cancel <nearest|uuid>`: cancela su recorrido.
  - `/universityguide guide remove <nearest|uuid>`: elimina el guía.
  - `/universityguide marker give`: entrega el bloque de parada únicamente si el operador está en creativo.

### Paradas invisibles

- Crear un bloque sin colisión que representa el punto exacto donde debe detenerse el guía.
- Solo será visible y seleccionable para el jugador local cuando esté en creativo, mediante un marcador translúcido renderizado del lado cliente.
- El servidor rechazará su colocación, edición o destrucción por jugadores que no sean creativos con permisos de operador.
- Cada parada tendrá una entidad de bloque con UUID inmutable, nombre de hasta 64 caracteres y explicación de texto plano de hasta 4096 caracteres.
- Clic derecho sobre el marcador abrirá una pantalla para editar y guardar esos campos.
- Solo se permitirá colocarlo sobre piso firme y con dos bloques transitables de altura.
- Mantener un índice por dimensión mediante `SavedData`, actualizado al colocar, editar o quitar paradas.

### Menú y recorrido

- Clic derecho en un guía disponible abrirá un menú con las paradas válidas de su dimensión, ordenadas alfabéticamente.
- Mostrar nombre y distancia; excluir destinos a más de 250 bloques.
- Al seleccionar, el servidor volverá a validar distancia, existencia del marcador, espacio transitable y que el NPC siga disponible.
- El grupo quedará formado por quien seleccionó y jugadores no espectadores situados a 16 bloques o menos.
- El guía navegará al centro del marcador usando pathfinding terrestre del servidor.
- Se detendrá cuando ningún participante conectado en la dimensión esté a 12 bloques y reanudará cuando alguno regrese.
- Si todos abandonan la dimensión o se desconectan durante 60 segundos, cancelará el recorrido.
- Si no obtiene progreso, realizará hasta tres recálculos durante 10 segundos; después avisará al grupo y cancelará.
- Al quedar a 1.5 bloques del destino, mostrará a los participantes una pantalla con título, texto ajustado y botón `Cerrar`.
- Quedará disponible en la parada; el siguiente viaje comenzará desde allí.
- Las solicitudes de menú, selección, edición y llegada usarán payloads NeoForge validados por el servidor.
- Persistir skin, nombre, destino, participantes y estado del guía; tras recargar, recalculará la ruta y esperará participantes.

## Pruebas y aceptación

- Compilar con `gradlew build` y arrancar cliente integrado y servidor dedicado.
- Verificar permisos, persistencia e índice de paradas al colocar, editar, romper y recargar.
- Comprobar que el marcador solo se ve y selecciona en creativo.
- Probar orden alfabético, límite de 250 bloques y rechazo de otra dimensión.
- Validar rutas con pasillos, esquinas, escaleras, losas y puertas; confirmar cancelación ante bloqueos.
- Simular multijugador para comprobar grupo, espera, reanudación, desconexión, guía ocupado y explicación.
- Probar skin válida, cuenta inexistente y servidor sin acceso al servicio de skins.

## Supuestos

- El campus está en una sola dimensión y cada recorrido mide como máximo 250 bloques.
- Los chunks se mantienen cargados por los jugadores o el servidor; el mod no fuerza su carga.
- Los caminos tienen al menos un bloque de ancho y dos de alto. Elevadores, escaleras verticales, rejas, puertas de hierro y rutas que requieran romper bloques quedan fuera.
- La primera versión solo incluye destinos directos; no incluye tours secuenciales, voz, minimapa ni editor de skins.
