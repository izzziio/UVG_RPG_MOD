# University Guide

Mod de guía turística para Minecraft Java 1.21.1 y NeoForge.

El NPC guía camina mediante pathfinding hacia paradas invisibles configurables y presenta información al grupo cuando llega.

## Desarrollo

Requiere Java 21. En Windows:

```powershell
.\validate_assets.ps1
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runClient
```

El validador comprueba JSON, referencias de modelos y paridad entre las traducciones en inglés y español.
Los GameTests verifican la serialización de paradas y las altas, cambios y bajas del índice por dimensión.

El JAR compilado se genera en `build/libs/`.

## Navegación y barreras (1.1.2)

El guía rodea bloques completos y muebles. Usa escaleras y losas según su colisión real, sin saltos sobre obstáculos ni caídas desde bordes. Mantiene la espera del grupo y las puertas compatibles. Si no encuentra una ruta completa, conserva los límites de reintento y avisa.

`/universityguide barrier give` entrega una barrera de exclusión. También puede obtenerse con `/give @s universityguide:guide_barrier`. Solo los jugadores en Creativo con permiso de operador nivel 2 pueden verla, seleccionarla, colocarla y retirarla. Sus franjas rojas y negras con una X marcan una celda que el cuerpo del guía no puede atravesar. No bloquea físicamente a jugadores ni otras criaturas. Los cambios actualizan las rutas afectadas.

NeoForge 21.1.248 sigue siendo el mínimo y la versión predeterminada; también se comprueba 21.1.250. Las pruebas de navegación cubren obstáculos, escaleras, losas, descansos, barreras, permisos, persistencia y puertas. La aceptación visual y las sesiones multijugador reales requieren una comprobación en el cliente.
El guía pasa por el espacio libre de las trampillas abiertas ajustando su posición a la hoja vertical y a su propio ancho. Busca un rodeo si la hoja bloquea el paso. Las trampillas cerradas que forman un suelo a ras siguen siendo transitables.

## Rutas despejadas (1.1.3)

La busqueda aplica un coste acotado a la cercania de colisiones reales y ajusta puntos hasta 0.2 bloques hacia el espacio libre. El margen es preferencia, no prohibicion de pasillos estrechos. Escaleras, losas, trampillas y barreras conservan sus verificaciones. Los atajos se comprueban cada cuatro ticks, hasta dos nodos y 2.5 bloques, usando colision completa; no se encadenan hasta un destino lejano.

El tour camina un 10 % mas rapido con un multiplicador que incluye NPC existentes. Una ruta interrumpida reintenta tras 10 ticks con el limite previo de tres intentos; la espera del grupo y el timeout se conservan. Las pruebas incluyen carriles abiertos, puntos centrados, giro estrecho en L y trampillas en corredores.


## Escaleras y prioridad de llegada (1.1.4)

En escaleras el ajuste de hasta0.2bloques se limita al ancho de la escalera: no adelanta ni retrasa el paso sobre el peldaño. Los peldaños transitables próximos no cuentan como paredes en la preferencia de espacio; las comprobaciones físicas de ascenso, descenso y colisión se mantienen.

Si la búsqueda con preferencia por espacio libre no alcanza el destino, se reintenta sin esa preferencia y con un presupuesto de búsqueda limitado a un multiplicador máximo de2. Llegar por una ruta segura tiene prioridad sobre elegir la más despejada. Las pruebas incluyen subida y bajada reales por escalera de tres carriles y centrado transversal en cuatro orientaciones. No demuestra acceso a todas las paradas de un mapa remoto sin revisarlo.


Al bajar peldaños, una falta momentanea de apoyo frena el movimiento horizontal sin borrar la ruta mientras cae hacia el siguiente escalon. Una vez apoyado se valida de nuevo el movimiento; no habilita saltos ni elimina controles de colision.


## Puertas CIT y giros (1.1.5)

Con Posted Signage 1.7.3 o posterior, el guia puede planear el paso por las puertas de vidrio CIT y las variantes sin texto, cortas y anchas. Consulta la forma de la hoja abierta, conserva el marco y abre la puerta al aproximarse. Un bloque en el giro impide planear o ejecutar la apertura. La puerta queda abierta para el grupo; no se cambian sus etiquetas. La integracion es opcional y el guia sigue funcionando sin Posted Signage. Hay que actualizar ambos mods en el servidor para activar esta compatibilidad.

Las formas se comparten dentro de cada comprobacion de movimiento; los puntos y transiciones de busqueda se descartan al terminar, para no conservar obstaculos antiguos. En los giros, el ajuste de espacio debe conectar tambien con la salida. Si ya alcanzo un punto y todavia esta descendiendo en el aire, frena horizontalmente y conserva la gravedad antes de seguir. Sobre suelo firme termina de centrarse normalmente. El detector de atasco cuenta avance horizontal: subir y bajar en el mismo sitio no reinicia su plazo.

La lista conserva el radio de 250 bloques. Estar en ese radio no garantiza una ruta. Se mantienen las restricciones de escaleras, losas, barreras y caidas, y la espera del grupo. Las pruebas incluyen descansos 2x2 y esquinas de escalera con barandales; la escalera concreta de un servidor necesita aceptacion alli.

## Instrucciones y presentación 1.1.6

El servidor entrega un libro vanilla de cinco páginas una sola vez por jugador. `/universityguide book` lo recupera sin permisos de operador; evita duplicados en el inventario y la mano secundaria. Si el inventario está lleno no reemplaza objetos ni marca la entrega como realizada. La marca persiste al morir y reconectar. Guías, marcadores y barreras conservan permiso2; el libro es la única rama pública.

El índice de paradas guarda un resumen de160caracteres para mostrarlo junto al nombre y distancia, sin cargar todos los chunks al abrir el menú. Las paradas antiguas refrescan su resumen al cargar; conservan la explicación completa. El protocolo de red pasa a2: actualizar Guide tanto en cliente como servidor.

La pantalla inicial UVG conserva las acciones vanilla (mundos, multijugador, Realms, Mods, opciones, idioma y accesibilidad) y los bloqueos multijugador. Menú clásico permite recuperar la apariencia original durante la sesión. El fondo es estático y dibujado con geometría sencilla. El equipo confirmó el recorrido/mapa preparados y definió el guía permanente como flujo definitivo.
