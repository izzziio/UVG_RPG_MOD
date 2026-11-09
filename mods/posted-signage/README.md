# Posted Signage

Mod de NeoForge para Minecraft Java 1.21.1. Agrega rótulos de seguridad y orientación que se colocan en paredes. Haz clic derecho en una cara vertical de pared para instalar uno. Al romperlo, o al retirar el bloque que lo sostiene, se suelta como objeto.

El mod también incluye una jardinera conectable de sansevieria, con follaje texturizado en dos planos cruzados en X, y un mural plano de seis figuras en forma de T, de diez bloques de ancho por cuatro de alto.

La colección de sanitarios usa rótulos 3D de doble cara que sobresalen perpendicularmente de la pared, con marco en relieve, placa de pared y soportes. Los demás rótulos de seguridad son paneles delgados de pared.

## Objetos

- `posted_signage:drinking_fountain`
- `posted_signage:customizable_hanging_sign`
- `posted_signage:customizable_hanging_sign_green`
- `posted_signage:customizable_hanging_sign_red`
- `posted_signage:customizable_hanging_sign_yellow`
- `posted_signage:customizable_hanging_sign_light_blue`
- `posted_signage:customizable_hanging_sign_purple`
- `posted_signage:customizable_hanging_sign_orange`
- `posted_signage:right_arrow_sign`
- `posted_signage:fire_extinguisher_sign`
- `posted_signage:emergency_exit_sign`
- `posted_signage:do_not_use_elevator_sign`
- `posted_signage:emergency_stairs_sign`
- `posted_signage:sansevieria_planter`
- `posted_signage:statue_relief`

### Modelos decorativos

- Coloca dos jardineras de sansevieria una junto a la otra para unirlas en una sola jardinera continua de dos bloques. Cada jardinera acepta solo una pareja, y la mitad restante vuelve a su modelo individual cuando se retira la otra.
- Coloca el mural desde la celda inferior izquierda de su tallo central de cuatro bloques de ancho, sobre una pared firme de diez de ancho por cuatro de alto. Un solo objeto instala las 28 partes de la T, y romper una parte retira todo el mural.

### Rótulos colgantes de sanitarios

Los diseños de caballeros, damas y mixto están disponibles en el azul original y en verde, rojo, naranja, amarillo y azul claro, para un total de 18 rótulos.

ID base:

- `posted_signage:mens_restroom_sign`
- `posted_signage:womens_restroom_sign`
- `posted_signage:mixed_restroom_sign`

Las variantes de color agregan uno de estos sufijos al ID base:

- `_green`
- `_red`
- `_orange`
- `_yellow`
- `_light_blue`

Todos los objetos están en la pestaña creativa **Señalización** y también se pueden obtener con `/give`.

## Cartel colgante CIT personalizable

Los carteles personalizables siguen el diseño de referencia CIT, ancho y blanco, y admiten un texto distinto en cada lado. Siete objetos separados ofrecen paneles CIT azul, verde, rojo, amarillo, azul claro, morado y naranja.

- Colócalo contra una pared vertical.
- Haz clic derecho en cualquiera de sus caras anchas con la mano vacía para editar las cuatro líneas de texto de esa cara.
- Elige la variante de color en la pestaña creativa o con `/give`.
- El fondo blanco y la marca CIT forman parte de la textura; solo se edita el nombre central del salón.
- El panel principal se mantiene blanco y las letras editables se mantienen oscuras para que sean legibles.
- La tinta luminosa y el panal conservan el comportamiento normal de los carteles vanilla.

## Bebedero e hidratación (1.4.0)

Instala el mismo JAR en el cliente y en el servidor. Obtén el bebedero en la pestaña creativa o con `/give @s posted_signage:drinking_fountain`. Colócalo contra dos caras sólidas de pared, una sobre la otra, y deja dos celdas libres al frente. Ocupa un espacio de 1x2x1 y conserva completos el pedestal, la pileta y el panel trasero. No necesita apoyo en el suelo, tanque ni tuberías.

Haz clic derecho en la barra frontal larga, debajo de la pileta, para beber. Al pulsarla aparece durante dos segundos un arco de partículas de agua con sonido. Cada jugador debe esperar tres segundos entre usos. Beber agrega seis puntos de hidratación, dos de hambre y dos de saturación, con los límites normales del juego, y apaga el fuego. Romper cualquiera de las dos mitades suelta un objeto en Supervivencia; en Creativo no suelta nada.

La hidratación va de 0 a 20 y se muestra como diez gotas sobre las barras de hambre y aire. Los jugadores en Supervivencia y Aventura pierden un punto por cada minuto de juego. En cero, la deshidratación aplica el equivalente a Debilidad I y Lentitud I; beber elimina esas penalizaciones sin quitar los efectos de pociones. La hidratación se conserva al guardar, al morir y al cambiar de dimensión. Los jugadores en Creativo y Espectador no pierden hidratación ni reciben penalizaciones. Esta versión no agrega recetas ni el llenado de botellas; quienes crean mapas colocan los bebederos desde Creativo o con comandos.

### Pruebas de desarrollo

`gradlew.bat runGameTestServer` ejecuta en un mundo aislado las pruebas automáticas del mod. Cubren el bebedero y la hidratación, las herramientas de construcción, las instalaciones activas, la orientación de texturas según el jugador, los objetos que se sueltan, las puertas, el vaciado del basurero y la migración de instalaciones antiguas. Las pruebas y su escenario no se incluyen en el JAR final. El validador de assets es `validate_assets.ps1`, en esta misma carpeta.

### Instalaciones funcionales

- Basurero: mide un bloque de alto y tiene nueve espacios persistentes. Haz clic derecho con un objeto en la mano para depositar la pila; lo que no quepa se queda en tu mano. Haz clic derecho con la mano principal vacía para abrirlo. El botón **Vaciar basurero** elimina permanentemente con un clic solo el contenido guardado, sin afectar tu inventario ni el objeto del cursor. Antes de vaciarlo, los objetos se pueden recuperar normalmente o con Mayús + clic. Cerrarlo conserva su contenido, y romperlo devuelve los objetos no eliminados. Ya no existe el gesto anterior de doble clic agachado.
- Puerta de vidrio ancha (2x3): haz clic derecho en cualquier parte para abrir o cerrar todo el panel. Las celdas que ocupa se mueven con el panel, y las de destino deben estar libres.
- Puerta de vidrio corta (1x2): haz clic derecho en cualquier parte para abrir o cerrar su única hoja.
- Las puertas actualizan su colisión, usan sonidos de puerta de metal y guardan si están abiertas. La interacción es manual, sin redstone.
- La antigua puerta de vidrio de 1x3 ya no está registrada ni aparece en la lista. Los ID antiguos de bloque y objeto apuntan ahora a la puerta corta. Al cargar un chunk se retiran, sin soltar objetos, las celdas obsoletas de la tercera fila de puertas y de la segunda fila de basureros.

### Compilación

Requiere Java 21.

```powershell
.\validate_assets.ps1
.\gradlew.bat build
```

El JAR distribuible se genera en `build/libs/`.
