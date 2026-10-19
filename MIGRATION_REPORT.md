# Informe de migración UVG_RPG_MOD

Base principal: `uvg-minecraft-mods-main.zip` (versión indicada como más reciente).
Referencia antigua: `UVG_RPG_MOD-main.zip`.

## Comparación tras normalizar rutas
- Archivos en la versión nueva: 425
- Archivos/rutas de referencia antigua: 538
- Rutas compartidas: 323
- Compartidos idénticos: 61
- Compartidos con contenido distinto: 262
- Exclusivos de la versión antigua: 215
- Exclusivos de la versión nueva: 102

## Política aplicada
Se conserva íntegra la estructura de la versión más reciente como proyecto principal. No se reemplaza código nuevo por versiones antiguas. Los archivos/rutas que solo aparecen en la referencia antigua se guardan en `legacy-review/` para revisión manual y posible recuperación selectiva; no se incorporan automáticamente al código activo porque pueden depender de APIs antiguas o assets ya reemplazados.

Los archivos auxiliares de comparación de la carpeta de trabajo anterior no se tratan como código activo.

## Verificación pendiente
No se ha confirmado una compilación de Gradle en este entorno. Antes de fusionar el Pull Request, ejecutar el flujo de validación de GitHub Actions y revisar especialmente los archivos de `legacy-review/`.
