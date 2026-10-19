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

La compilación local verificada se conserva en
`releases/universityguide-1.0.0-local-tested.jar`. La versión que se desarrolló en otra
computadora ya fue aplicada al worktree actual; sus cambios de interfaz y traducciones deben
terminar de probarse y confirmarse antes de publicar el JAR definitivo.
