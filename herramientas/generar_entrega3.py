"""Genera el documento de la tercera entrega a partir del estado verificable del repositorio."""

from pathlib import Path

from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT, WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[1]
OUTPUT_DIR = ROOT / "entrega-3"
OUTPUT_PATH = OUTPUT_DIR / "Entrega 3 - UVG Minecraft Mods.docx"

GREEN = "00563F"
LIGHT_GREEN = "DDEBE5"
GOLD = "F3C64D"
LIGHT_GOLD = "FFF4CC"
GRAY = "E9ECEF"
WHITE = "FFFFFF"
TEXT = RGBColor(31, 41, 55)


def shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = tc_pr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tc_pr.append(shd)
    shd.set(qn("w:fill"), fill)


def set_cell_text(cell, value, bold=False, color=None, size=8.5):
    cell.text = ""
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    run = p.add_run(str(value))
    run.bold = bold
    run.font.name = "Aptos"
    run.font.size = Pt(size)
    if color:
        run.font.color.rgb = RGBColor.from_string(color)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER


def add_table(doc, headers, rows, widths=None, font_size=8.5):
    table = doc.add_table(rows=1, cols=len(headers))
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.style = "Table Grid"
    table.autofit = False
    for index, header in enumerate(headers):
        set_cell_text(table.rows[0].cells[index], header, True, WHITE, font_size)
        shade(table.rows[0].cells[index], GREEN)
        if widths:
            table.rows[0].cells[index].width = Inches(widths[index])
    for row_index, values in enumerate(rows):
        cells = table.add_row().cells
        for index, value in enumerate(values):
            set_cell_text(cells[index], value, False, None, font_size)
            if widths:
                cells[index].width = Inches(widths[index])
            if row_index % 2:
                shade(cells[index], "F7F9F8")
    doc.add_paragraph().paragraph_format.space_after = Pt(0)
    return table


def add_heading(doc, text, level=1):
    p = doc.add_heading(text, level=level)
    p.paragraph_format.keep_with_next = True
    return p


def add_body(doc, text, bold_lead=None):
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(6)
    if bold_lead and text.startswith(bold_lead):
        p.add_run(bold_lead).bold = True
        p.add_run(text[len(bold_lead):])
    else:
        p.add_run(text)
    return p


def add_bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        p.paragraph_format.space_after = Pt(3)
        p.add_run(item)


def add_note(doc, title, text, warning=False):
    table = doc.add_table(rows=1, cols=1)
    table.style = "Table Grid"
    cell = table.cell(0, 0)
    shade(cell, LIGHT_GOLD if warning else LIGHT_GREEN)
    p = cell.paragraphs[0]
    p.paragraph_format.space_after = Pt(0)
    r = p.add_run(f"{title}: ")
    r.bold = True
    r.font.color.rgb = RGBColor.from_string(GREEN)
    p.add_run(text)
    doc.add_paragraph().paragraph_format.space_after = Pt(0)


def add_page_number(paragraph):
    paragraph.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = paragraph.add_run("Página ")
    fld = OxmlElement("w:fldSimple")
    fld.set(qn("w:instr"), "PAGE")
    run._r.addnext(fld)


def configure_document(doc):
    section = doc.sections[0]
    section.top_margin = Inches(0.65)
    section.bottom_margin = Inches(0.6)
    section.left_margin = Inches(0.65)
    section.right_margin = Inches(0.65)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = "Aptos"
    normal.font.size = Pt(10)
    normal.font.color.rgb = TEXT
    normal.paragraph_format.space_after = Pt(5)
    normal.paragraph_format.line_spacing = 1.05
    for name, size, color in (("Title", 25, GREEN), ("Heading 1", 16, GREEN),
                              ("Heading 2", 12, GREEN), ("Heading 3", 10.5, GREEN)):
        style = styles[name]
        style.font.name = "Aptos Display"
        style.font.size = Pt(size)
        style.font.bold = True
        style.font.color.rgb = RGBColor.from_string(color)
    styles["Heading 1"].paragraph_format.space_before = Pt(12)
    styles["Heading 1"].paragraph_format.space_after = Pt(6)

    header = section.header.paragraphs[0]
    header.text = "UVG · CC2006 Programación Orientada a Objetos · Entrega 3"
    header.style = styles["Caption"]
    header.runs[0].font.color.rgb = RGBColor.from_string(GREEN)
    footer = section.footer.paragraphs[0]
    add_page_number(footer)

    settings = doc.settings.element
    if settings.find(qn("w:trackRevisions")) is None:
        settings.append(OxmlElement("w:trackRevisions"))


def add_cover(doc):
    for _ in range(3):
        doc.add_paragraph()
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("UNIVERSIDAD DEL VALLE DE GUATEMALA")
    r.bold = True
    r.font.name = "Aptos Display"
    r.font.size = Pt(14)
    r.font.color.rgb = RGBColor.from_string(GREEN)
    for line in ("Facultad de Ingeniería", "Departamento de Ciencias de la Computación",
                 "CC2006 – Programación Orientada a Objetos", "Semestre II – 2026"):
        p = doc.add_paragraph(line)
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(2)
    doc.add_paragraph()
    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("TERCERA ENTREGA DEL PROYECTO")
    run.bold = True
    run.font.name = "Aptos Display"
    run.font.size = Pt(27)
    run.font.color.rgb = RGBColor.from_string(GREEN)
    subtitle = doc.add_paragraph("UVG Minecraft Mods")
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    subtitle.runs[0].font.size = Pt(20)
    subtitle.runs[0].font.bold = True
    doc.add_paragraph()
    p = doc.add_paragraph("Planificación · Persistencia de datos · Implementación · Gestión")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(12)
    p.runs[0].font.color.rgb = RGBColor.from_string(GREEN)
    doc.add_paragraph()
    p = doc.add_paragraph("Repositorio: https://github.com/jacobjuarezguerra/uvg-minecraft-mods")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.runs[0].font.size = Pt(9)
    p = doc.add_paragraph("Fecha de preparación: 24 de septiembre de 2026")
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.add_page_break()


def add_team(doc):
    add_heading(doc, "Datos del equipo")
    add_note(doc, "Verificación necesaria",
             "Los nombres completos fueron confirmados por el equipo. El correo institucional de Diego Marroquín contiene 261402; se muestra como carné probable y debe confirmarse. Los demás carnés no aparecen en el repositorio.", True)
    add_table(doc, ["Integrante", "Carné", "Participación comprobable"], [
        ["Jacob Juárez Guerra", "Por confirmar", "Integración, estructura Gradle, registro y funcionalidad de ambos mods"],
        ["Diego Marroquín", "261402*", "Navegación del guía, pantallas, renderizado y recursos de University Guide"],
        ["Oscar Cruz", "Por confirmar", "Comportamiento interactivo de señales, macetero conectado y mural"],
        ["Ricardo Sandoval", "Por confirmar", "Recursos visuales, estados, modelos, traducciones y validación de assets"],
    ], [1.55, 1.05, 4.55])
    add_heading(doc, "Control del documento", 2)
    add_table(doc, ["Versión", "Fecha", "Cambio", "Responsable"], [
        ["0.1", "24/09/2026", "Documento inicial basado en la guía y en evidencia del repositorio", "Equipo"],
        ["0.2", "24/09/2026", "Actividad individual de Git incorporada a formularios y reflexión", "Equipo"],
        ["1.0", "Antes de entregar", "Nombres, carnés, tiempos reales y retroalimentación confirmados", "Equipo"],
    ], [0.7, 1.0, 4.25, 1.2])
    add_note(doc, "Control de cambios", "El seguimiento de cambios queda activado para las ediciones posteriores en Word.")
    add_heading(doc, "Contribución comprobable en Git", 2)
    add_table(doc, ["Autor", "Commits en main", "Áreas observadas", "Criterio >3"], [
        ["Jacob Juárez Guerra", "6+", "Integración, builds y creación segura del guía", "Cumple"],
        ["Diego Marroquín (Git: Diego)", "4", "Navegación, persistencia, pantallas y borradores", "Cumple"],
        ["Oscar Cruz (Git: Oskar)", "4", "Señales, transformaciones, macetero y mural", "Cumple"],
        ["Ricardo Sandoval (Git: Ricardo)", "8+", "Recursos, validadores, CI, compilación y entrega", "Cumple"],
    ], [1.75, 1.0, 3.55, 0.95])
    add_body(doc, "Conteo obtenido de los commits alcanzables desde la rama main; no incluye ramas locales de respaldo.")


def add_summary(doc):
    add_heading(doc, "Resumen ejecutivo")
    add_body(doc, "UVG Minecraft Mods es un monorepositorio con dos mods independientes para Minecraft Java 1.21.1 y NeoForge. Posted Signage incorpora 32 bloques de orientación y ambientación. University Guide añade un NPC que permite seleccionar paradas, caminar hacia ellas con un grupo y mostrar información al llegar.")
    add_body(doc, "La entrega demuestra persistencia nativa sin infraestructura externa. Minecraft conserva propiedades de bloques en chunks; los letreros editables usan SignBlockEntity; las paradas guardan UUID, nombre y descripción mediante BlockEntity; el índice global usa SavedData; y el NPC guarda en NBT el destino, participantes y datos de apariencia necesarios para reanudar de forma segura.")
    add_body(doc, "El 24 de septiembre de 2026 se ejecutó la compilación offline de ambos módulos. Posted Signage y University Guide finalizaron con BUILD SUCCESSFUL. Además, el validador especializado aprobó 160 archivos JSON y los conjuntos de recursos de los 32 bloques registrados.")
    add_note(doc, "Criterio de evidencia", "Este informe distingue implementación inspeccionada, validación automatizada y prueba manual. Una función compilada no se presenta como validada dentro del juego mientras no exista un registro real de la sesión.")
    add_heading(doc, "Propósito y usuarios", 2)
    add_table(doc, ["Usuario", "Necesidad", "Respuesta del sistema"], [
        ["Visitante", "Reconocer rutas y servicios del campus", "Señales de dirección, seguridad y sanitarios"],
        ["Estudiante de primer ingreso", "Conocer ubicaciones y su función", "Guía NPC, destinos configurables y explicación al llegar"],
        ["Administrador del mapa", "Actualizar recorridos sin recompilar", "Paradas editables, nombre, descripción y comandos administrativos"],
        ["Constructor del campus", "Ambientar espacios de forma coherente", "Letreros CIT, maceteros conectables y mural multiparte"],
    ], [1.55, 2.4, 3.6])


def add_assignments(doc):
    add_heading(doc, "Asignación de revisión y autoría")
    add_body(doc, "Cada integrante tiene una parte técnica y una reflexión asignadas según sus contribuciones comprobables. El seguimiento de cambios está activado: cada persona debe abrir el archivo con su nombre configurado en Word, revisar su sección y realizar al menos una corrección real.")
    add_table(doc, ["Integrante", "Secciones asignadas", "Revisión final que debe realizar"], [
        ["Jacob Juárez Guerra", "Resumen, requisitos, planificación e investigación de persistencia", "Confirmar prioridades, decisión técnica y coordinación del plan"],
        ["Diego Marroquín", "University Guide, navegación, red, pantallas y persistencia del NPC", "Verificar la descripción del recorrido y ajustar su reflexión personal"],
        ["Oscar Cruz", "Señales interactivas, letreros, macetero y mural", "Confirmar reglas de colocación y ajustar su reflexión personal"],
        ["Ricardo Sandoval", "Recursos, idiomas, modelos, validación y muestra a usuarios", "Confirmar evidencia de assets y ajustar su reflexión personal"],
        ["Equipo completo", "Resultados con usuarios, formularios de tiempo y revisión para Canvas", "Agregar datos reales y aceptar o rechazar los cambios finales"],
    ], [1.55, 3.05, 2.8], 8)
    add_note(doc, "Identidad de las revisiones", "El nombre que aparece en Control de cambios depende de la cuenta o del nombre de usuario configurado en Word. Esta asignación no sustituye la revisión real de cada integrante.", True)


def add_requirements(doc):
    add_heading(doc, "1. Requisitos funcionales priorizados")
    add_body(doc, "La prioridad considera valor para el recorrido, dependencias técnicas y riesgo. Cada requisito se considera terminado cuando puede demostrarse de extremo a extremo dentro del juego.")
    rows = [
        ["RF-01", "Crítica", "Configurar paradas con nombre y descripción", "Un administrador edita una parada; los datos permanecen tras recargar", "Jacob / Diego Marroquín", "Implementado*"],
        ["RF-02", "Crítica", "Seleccionar un destino y comenzar un recorrido", "El guía lista paradas válidas hasta 250 bloques y acepta la selección", "Jacob / Diego Marroquín", "Implementado*"],
        ["RF-03", "Crítica", "Guiar a un grupo hasta la parada", "El NPC camina, espera al grupo, reintenta ruta y muestra la llegada", "Diego Marroquín", "Implementado*"],
        ["RF-04", "Alta", "Colocar señalización institucional", "Cada señal se orienta, exige soporte y aparece en la pestaña creativa", "Oscar Cruz / Ricardo Sandoval", "Implementado*"],
        ["RF-05", "Alta", "Editar ambas caras de letreros CIT", "El texto frontal y posterior se renderiza y persiste", "Oscar Cruz / Ricardo Sandoval", "Implementado*"],
        ["RF-06", "Alta", "Conectar maceteros contiguos", "La pareja se conecta recíprocamente y se separa al retirar un bloque", "Oscar Cruz", "Implementado*"],
        ["RF-07", "Media", "Colocar mural compuesto", "Un ítem instala 28 partes solo si existe espacio y soporte", "Oscar Cruz / Ricardo Sandoval", "Implementado*"],
        ["RF-08", "Media", "Localizar y completar recursos", "Bloques con modelo, estado, botín y traducción en tres idiomas", "Ricardo Sandoval", "Validado"],
    ]
    add_table(doc, ["ID", "Prioridad", "Requisito", "Criterio de aceptación", "Responsable", "Estado"], rows,
              [0.55, 0.7, 1.55, 2.65, 1.0, 0.7], 7.4)
    add_body(doc, "*Implementado en código y compilado; la aceptación funcional dentro del juego permanece pendiente de ejecución documentada.")
    add_heading(doc, "Requisitos no funcionales", 2)
    add_table(doc, ["ID", "Área", "Medida verificable"], [
        ["RNF-01", "Compatibilidad", "Minecraft 1.21.1, Java 21, NeoForge 21.1.248/21.1.250 y Gradle 9.2.1"],
        ["RNF-02", "Autoridad", "Cambios de paradas validados en servidor por permisos, distancia y longitud"],
        ["RNF-03", "Integridad", "Recursos JSON válidos y referencias locales completas"],
        ["RNF-04", "Persistencia", "Datos ligados al mundo sin requerir un servicio de base de datos"],
        ["RNF-05", "Mantenibilidad", "Dos módulos independientes, paquetes por responsabilidad y compilación reproducible"],
    ], [0.7, 1.25, 5.4])


def add_persistence(doc):
    add_heading(doc, "2. Investigación de persistencia de datos")
    add_body(doc, "El sistema conserva datos espaciales y de entidades dentro de mundos que pueden ejecutarse localmente o en servidores. Se compararon las alternativas por integración con Minecraft, consistencia con chunks y dimensiones, portabilidad, concurrencia, dependencias y costo de mantenimiento.")
    add_table(doc, ["Tecnología", "Ventajas", "Limitaciones", "Decisión"], [
        ["BlockState", "Serialización automática por chunk; ideal para estados pequeños y visibles", "Número limitado de propiedades y valores", "Usar para orientación, conexión y pieza del mural"],
        ["BlockEntity / NBT", "Datos ricos por posición y sincronización con cliente", "No conviene como índice global", "Usar para textos y datos completos de paradas"],
        ["SavedData", "Índice global persistente por dimensión; acceso eficiente", "Debe mantenerse consistente al crear o eliminar bloques", "Usar para localizar paradas por UUID"],
        ["Datos de entidad / NBT", "Viajan con la entidad y permiten recuperar su sesión", "Requieren validar destino al cargar", "Usar para estado y destino del guía"],
        ["JSON / datapacks", "Legibles y apropiados para contenido declarativo", "No guardan cambios dinámicos de una partida", "Usar para modelos, traducciones y contenido base"],
        ["SQLite", "Consultas, índices y transacciones en un archivo", "Dependencia y respaldo separados del mundo", "Descartar para el alcance actual"],
        ["SQL externo", "Centralización y consultas multiusuario", "Credenciales, red, operación y menor portabilidad", "Descartar para el alcance actual"],
    ], [1.2, 2.25, 2.05, 1.7], 7.6)
    add_heading(doc, "Decisión y justificación", 2)
    add_body(doc, "Se selecciona la persistencia nativa e híbrida de Minecraft. Los datos quedan junto al mundo, respetan el ciclo de guardado de chunks y entidades, funcionan sin conexión externa y se respaldan al copiar la partida. La decisión también evita credenciales y fallas de red para un volumen pequeño de información espacial.")
    add_bullets(doc, [
        "Posted Signage: FACING, CONNECTION y PART se almacenan como BlockState; el texto editable aprovecha SignBlockEntity.",
        "University Guide: TourStopBlockEntity conserva UUID, nombre y descripción; TourStopSavedData mantiene el índice de UUID, posición y nombre.",
        "GuideEntity serializa destino, participantes, cuenta de skin y perfil; al cargar vuelve a un estado seguro y valida la parada.",
        "Los cinco payloads solo transportan acciones y vistas; la autoridad y el estado persistente permanecen en el servidor.",
    ])
    add_heading(doc, "Prueba de persistencia", 2)
    add_table(doc, ["Paso", "Acción", "Resultado esperado"], [
        ["1", "Crear una parada, asignar nombre y descripción", "El índice global incluye su UUID y posición"],
        ["2", "Editar texto distinto en cada cara de un letrero", "Ambas caras muestran el texto correcto"],
        ["3", "Iniciar un recorrido y guardar el mundo", "Destino y participantes se escriben en los datos del NPC"],
        ["4", "Cerrar por completo y reabrir el mismo mundo", "Parada, textos, estados de bloques y datos del guía se conservan"],
        ["5", "Eliminar una parada seleccionada", "El guía cancela de forma segura y el índice elimina la entrada inválida"],
    ], [0.55, 3.1, 3.55])
    add_heading(doc, "Fuentes consultadas", 2)
    add_bullets(doc, [
        "NeoForge 1.21–1.21.1 Documentation, Block Entities: https://docs.neoforged.net/docs/1.21.1/blockentities/",
        "NeoForge 1.21–1.21.1 Documentation, Saved Data: https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/",
        "Minecraft Wiki, NBT format: https://minecraft.wiki/w/NBT_format",
        "SQLite Documentation, Appropriate Uses for SQLite: https://www.sqlite.org/whentouse.html",
        "PostgreSQL Documentation, Client Authentication: https://www.postgresql.org/docs/current/client-authentication.html",
    ])
    add_body(doc, "Consulta: 24/09/2026. La selección se contrastó además con las clases de persistencia implementadas en ambos módulos.")


def add_planning(doc):
    add_heading(doc, "3. Planificación detallada")
    tasks = [
        ["T01", "Inventario y prioridades", "Relacionar funciones reales con requisitos y criterios", "2.0", "Jacob", "21/09", "RF-01–08"],
        ["T02", "Registro e integración", "Integrar entradas, registros y eventos de ambos módulos", "3.0", "Jacob", "22/09", "RF-01–08"],
        ["T03", "Persistencia de paradas", "Revisar BlockEntity, UUID, NBT e índice SavedData", "2.5", "Jacob", "23/09", "RF-01"],
        ["T04", "Estado del recorrido", "Validar inicio, espera, llegada y cancelación", "3.0", "Diego Marroquín", "22/09", "RF-02–03"],
        ["T05", "Pathfinding y recuperación", "Probar distancias, reintentos y ruta bloqueada", "2.5", "Diego Marroquín", "23/09", "RF-03"],
        ["T06", "Pantallas y renderizado", "Comprobar selección, editor, llegada y render del NPC", "2.5", "Diego Marroquín", "24/09", "RF-01–03"],
        ["T07", "Señales y soporte", "Validar orientación, supervivencia y retiro del soporte", "2.0", "Oscar Cruz", "22/09", "RF-04"],
        ["T08", "Letreros editables", "Revisar interacción, dos caras y persistencia", "2.5", "Oscar Cruz", "23/09", "RF-05"],
        ["T09", "Macetero conectado", "Validar los cinco estados y reciprocidad", "2.0", "Oscar Cruz", "23/09", "RF-06"],
        ["T10", "Mural multiparte", "Probar espacio, soporte, 28 piezas y eliminación", "3.0", "Oscar Cruz", "24/09", "RF-07"],
        ["T11", "Recursos de señales", "Completar modelos, texturas, blockstates y loot", "3.0", "Ricardo Sandoval", "22/09", "RF-04–05"],
        ["T12", "Recursos decorativos", "Completar modelos del macetero y mural", "3.0", "Ricardo Sandoval", "23/09", "RF-06–07"],
        ["T13", "Idiomas y catálogo", "Revisar en_us, es_es, es_mx y pestaña creativa", "2.0", "Ricardo Sandoval", "24/09", "RF-08"],
        ["T14", "Validación automatizada", "Ejecutar validador y corregir referencias", "2.0", "Ricardo Sandoval", "24/09", "RF-04–08"],
        ["T15", "Compilación integrada", "Compilar ambos proyectos con Java 21", "1.5", "Equipo", "24/09", "Todos"],
        ["T16", "Muestra con usuarios", "Ejecutar guion con dos perfiles y registrar resultados", "2.0", "Equipo", "Antes de entrega", "Todos"],
        ["T17", "Documento final", "Confirmar datos, tiempos, Git y evidencias", "2.5", "Equipo", "Antes de entrega", "Todos"],
    ]
    add_table(doc, ["ID", "Tarea", "Descripción", "h", "Resp.", "Fin", "RF"], tasks,
              [0.45, 1.25, 2.7, 0.35, 0.7, 0.65, 0.75], 7.1)
    add_heading(doc, "Calendario", 2)
    add_table(doc, ["Fecha", "Tareas", "Hito / evidencia"], [
        ["21/09", "T01", "Alcance y requisitos identificados"],
        ["22/09", "T02, T04, T07, T11", "Arquitectura y comportamientos principales integrados"],
        ["23/09", "T03, T05, T08, T09, T12", "Persistencia y componentes especializados revisados"],
        ["24/09", "T06, T10, T13, T14, T15", "Recursos validados y dos builds exitosos"],
        ["Antes de entregar", "T16", "Retroalimentación de dos usuarios registrada"],
        ["Antes de entregar", "T17", "Datos personales, formularios y commits confirmados"],
    ], [1.25, 2.1, 4.05])


def add_implementation(doc):
    add_heading(doc, "4. Implementación y evidencia")
    add_table(doc, ["Área", "Implementación", "Evidencia verificable"], [
        ["Posted Signage", "32 bloques registrados: 5 señales planas, 18 de sanitarios, 7 CIT, 1 macetero y 1 mural", "PostedSignage.java y 160 JSON de recursos"],
        ["Letreros CIT", "Edición por cara mediante SignBlockEntity y renderer propio", "CustomizableHangingSignBlock(Entity/Renderer)"],
        ["Decoración", "Conexión recíproca del macetero y mural de 28 partes", "ConnectingPlanterBlock y StatueReliefBlock"],
        ["Paradas", "UUID, nombre, descripción e índice persistente", "TourStopBlockEntity y TourStopSavedData"],
        ["Guía", "Selección, grupo, estados IDLE/WALKING/WAITING, ruta y llegada", "GuideEntity"],
        ["Red", "Cinco payloads; validación de permisos, distancia y límites", "TourNetwork y TourPayloads"],
        ["Interfaz", "Pantallas de destinos, edición y llegada", "DestinationScreen, StopEditorScreen y ArrivalScreen"],
    ], [1.15, 3.45, 2.8], 8)
    add_heading(doc, "Resultados ejecutados el 24/09/2026", 2)
    add_table(doc, ["Comprobación", "Resultado", "Alcance"], [
        ["validate_assets.ps1", "APROBADO", "160 JSON y 32 conjuntos de recursos registrados"],
        ["Gradle build · posted-signage", "BUILD SUCCESSFUL", "Compilación, recursos, jar, assemble y check"],
        ["Gradle build · university-guide", "BUILD SUCCESSFUL", "Compilación, recursos, jar, assemble y check"],
        ["Pruebas Java", "NO-SOURCE", "No existen pruebas unitarias Java en los módulos"],
        ["Prueba dentro del juego", "PENDIENTE", "Debe ejecutarse antes de afirmar aceptación visual y persistencia real"],
    ], [2.15, 1.3, 3.95])
    add_note(doc, "Interpretación", "La compilación y la validación de recursos demuestran consistencia estática. Las interacciones dentro de Minecraft todavía requieren una prueba manual documentada.", True)
    add_heading(doc, "Tareas de implementación pendientes", 2)
    add_bullets(doc, [
        "Ejecutar la matriz de prueba manual dentro de Minecraft y registrar resultado observado, responsable y fecha.",
        "Comprobar persistencia cerrando por completo y reabriendo el mismo mundo.",
        "Verificar convivencia de ambos JAR con una versión de NeoForge compatible con sus metadatos.",
        "Añadir pruebas automatizadas Java o GameTest para estados, soporte, conexiones y recorridos en una iteración posterior.",
    ])


def add_version_control(doc):
    add_heading(doc, "5. Controlador de versiones")
    add_body(doc, "Repositorio compartido: https://github.com/jacobjuarezguerra/uvg-minecraft-mods")
    add_table(doc, ["Autor registrado", "Commits visibles", "Fecha observada", "Criterio >3/semana"], [
        ["Jacob Juárez Guerra", "6+", "21–24/09/2026", "Cumple"],
        ["Diego Marroquín (Git: Diego)", "4", "21–24/09/2026", "Cumple"],
        ["Oscar Cruz (Git: Oskar)", "4", "21–24/09/2026", "Cumple"],
        ["Ricardo Sandoval (Git: Ricardo)", "8+", "21–24/09/2026", "Cumple"],
    ], [2.0, 1.15, 1.55, 2.6])
    add_note(doc, "Criterio satisfecho", "Los cuatro integrantes superan tres commits en main y cuentan con cambios de programación. Falta publicar los commits locales para que el conteo sea visible en GitHub.")
    add_body(doc, "Antes de entregar el vínculo, se deben subir los commits locales y comprobar que GitHub muestre el código, las validaciones y el documento actualizado.")


def add_user_review(doc):
    add_heading(doc, "6. Muestra a usuarios finales")
    add_body(doc, "La guía solicita tomar en cuenta la opinión de futuros usuarios. El protocolo siguiente evita confundir una demostración preparada con resultados reales. Deben participar, como mínimo, una persona que no conozca el mapa y una persona familiarizada con la UVG.")
    add_table(doc, ["Paso", "Actividad", "Pregunta"], [
        ["1", "Localizar un servicio siguiendo solo las señales", "¿Qué señal fue clara o confusa?"],
        ["2", "Seleccionar un destino con el guía y seguirlo", "¿Se entendió cuándo caminar y cuándo esperar?"],
        ["3", "Editar una parada y un letrero; reiniciar el mundo", "¿Los nombres y mensajes fueron fáciles de modificar y permanecieron?"],
        ["4", "Explorar mural y maceteros durante el recorrido", "¿La ambientación ayudó a reconocer el espacio?"],
    ], [0.55, 3.35, 3.6])
    add_heading(doc, "Ensayo simulado de resultados", 2)
    add_note(doc, "Datos simulados", "Estas filas anticipan resultados plausibles para practicar la demostración. Deben confirmarse o sustituirse después de ejecutar la sesión con participantes reales.", True)
    add_table(doc, ["Usuario / perfil", "Resultado simulado", "Comentario de ensayo", "Acción propuesta"], [
        ["U1 · visitante", "Localizó la salida y los sanitarios; dudó ante las variantes de color", "Las señales se entienden, pero no sabía si cada color tenía un significado distinto", "Explicar que el color identifica variantes visuales y mantener pictogramas consistentes"],
        ["U2 · persona familiarizada con UVG", "Seleccionó Biblioteca y siguió al guía; se sorprendió cuando el NPC esperó", "La selección fue clara; conviene avisar que el guía se detiene si el grupo se aleja", "Añadir una indicación breve sobre la regla de espera antes de iniciar el recorrido"],
    ], [1.6, 1.55, 2.75, 1.6])


def add_reflection(doc):
    add_heading(doc, "7. Reflexión preliminar del equipo")
    add_bullets(doc, [
        "La separación en dos proyectos Gradle permitió trabajar en orientación y recorridos sin crear dependencia directa entre los mods.",
        "El uso de tipos nativos de Minecraft redujo infraestructura, pero obliga a distinguir datos por bloque, datos globales y datos de entidad.",
        "La validación automatizada de assets resultó especialmente útil porque Posted Signage contiene muchas variantes que serían costosas de revisar manualmente.",
        "La cobertura de pruebas es desigual: existen validaciones de recursos y builds reproducibles, pero no hay pruebas Java automatizadas ni evidencia de una sesión completa dentro del juego.",
        "La distribución final cumple el mínimo semanal: cada integrante tiene al menos cuatro commits alcanzables desde main y aportes de programación identificables.",
        "La siguiente iteración debe registrar tiempos durante el trabajo y no reconstruirlos al final; eso hará útil la comparación entre horas estimadas y reales.",
    ])
    add_heading(doc, "Evidencia individual y borrador de reflexión", 2)
    add_table(doc, ["Integrante", "Aporte observable", "Reflexión que debe confirmar personalmente"], [
        ["Jacob Juárez Guerra", "Integró ambos proyectos, registros, scaffolds y funcionalidad base.", "La integración temprana permitió trabajar sobre una estructura común; conviene repartir los commits con mayor continuidad."],
        ["Diego Marroquín", "Implementó estado, navegación, pantallas, renderizado y recursos del guía.", "Separar servidor, payloads e interfaz redujo responsabilidades mezcladas; faltan pruebas automatizadas del recorrido."],
        ["Oscar Cruz", "Implementó señales interactivas, conexión del macetero y mural multiparte.", "Los bloques compuestos exigieron validar vecinos, soporte y eliminación; conviene añadir casos automatizados de cambios de estado."],
        ["Ricardo Sandoval", "Añadió recursos, validador, reorganización, automatización y documentación.", "La validación sistemática redujo referencias rotas; la reorganización y los commits posteriores mejoraron la trazabilidad del trabajo."],
    ], [1.55, 2.8, 3.55], 8)
    add_note(doc, "Confirmación personal", "Estas reflexiones se derivan del historial y del código. Cada integrante debe revisarlas y ajustarlas para que expresen su experiencia real.", True)
    add_heading(doc, "Reflexiones individuales desarrolladas", 2)
    add_heading(doc, "Jacob Juárez Guerra", 3)
    add_body(doc, "Mi aporte principal fue establecer e integrar la estructura de los dos mods. Trabajé en la configuración de Gradle y NeoForge, los registros de contenido y la funcionalidad base que conecta bloques, entidades, red y recursos. La dificultad más importante fue mantener independientes los módulos sin duplicar decisiones de arquitectura. Para resolverla, cada mod conserva su propio identificador, build y punto de entrada, mientras el repositorio comparte documentación y herramientas. El resultado verificable es que ambos módulos compilan de forma independiente y pueden distribuirse como JAR separados. En una siguiente entrega mejoraría la coordinación del historial de Git para que las contribuciones se registren de forma más gradual y equilibrada.")
    add_heading(doc, "Diego Marroquín", 3)
    add_body(doc, "Mi trabajo se concentró en University Guide: la máquina de estados del NPC, su navegación, las pantallas y el renderizado. El reto fue coordinar comportamiento del servidor e interfaz del cliente sin permitir que una pantalla modificara directamente el estado del mundo. La solución separa los payloads, la validación de servidor y las pantallas de presentación. El guía puede iniciar un recorrido, esperar cuando el grupo se aleja, reintentar la ruta y mostrar la información de llegada. La compilación confirma que las piezas están integradas; para mejorar el módulo agregaría pruebas automatizadas de los cambios entre IDLE, WALKING y WAITING, además de casos de ruta bloqueada.")
    add_heading(doc, "Oscar Cruz", 3)
    add_body(doc, "Mi responsabilidad fue implementar el comportamiento de las señales y los elementos decorativos de Posted Signage. Las partes más exigentes fueron la conexión recíproca de los maceteros y el mural compuesto, porque un cambio en un bloque debe mantener coherentes sus vecinos y su soporte. Organicé esos comportamientos mediante propiedades de BlockState, enums y validaciones durante la colocación y actualización. El resultado es un conjunto de elementos que responde al entorno y conserva su estado con el mundo. Como mejora, propondría pruebas de GameTest que coloquen y retiren automáticamente soportes, vecinos y partes del mural para detectar regresiones.")
    add_heading(doc, "Ricardo Sandoval", 3)
    add_body(doc, "Mi aporte se enfocó en los recursos de Posted Signage: modelos, estados, texturas, traducciones y tablas de botín para señales y decoración. El principal reto fue conservar correspondencia entre muchos identificadores y variantes sin dejar referencias rotas. Para reducir ese riesgo se incorporó un validador que compara los bloques registrados con sus blockstates, modelos, loot tables, traducciones y texturas. La ejecución revisó 160 archivos JSON y 32 conjuntos de recursos sin errores. En una próxima entrega dividiría el trabajo en commits más pequeños y ampliaría la validación visual dentro del juego para complementar las comprobaciones estáticas.")


def add_time_form(doc, name, carnet, rows):
    doc.add_page_break()
    add_heading(doc, "Formulario 1 · Gestión del tiempo · borrador estimado")
    p = doc.add_paragraph()
    p.add_run("Nombre: ").bold = True
    p.add_run(name)
    p.add_run("        Carné: ").bold = True
    p.add_run(carnet)
    add_body(doc, "Los horarios son una reconstrucción estimada a partir de las tareas y commits; no prueban horas reales. Cada integrante debe confirmarlos o corregirlos. Delta = (Fin − Inicio) − interrupciones.")
    display_rows = [list(row) for row in rows]
    while len(display_rows) < 10:
        display_rows.append(["", "", "", "", "", "", ""])
    add_table(doc, ["Fecha", "Inicio", "Fin", "Interr. min", "Delta min", "Tarea", "Comentarios"], display_rows,
              [0.75, 0.65, 0.65, 0.75, 0.75, 1.35, 2.2], 7.5)
    total = sum(int(row[4]) for row in rows)
    add_table(doc, ["Total real", "Reflexión individual"], [
        [f"Estimado: {total} min ({total / 60:.1f} h)", "Confirmar horarios y ajustar la reflexión con la experiencia personal"],
    ], [1.6, 5.5])


def add_pending(doc):
    doc.add_page_break()
    add_heading(doc, "8. Pendientes antes de entregar")
    add_table(doc, ["Pendiente", "Responsable", "Prioridad", "Criterio de cierre"], [
        ["Confirmar los carnés de los cuatro integrantes", "Equipo", "Crítica", "Portada y formularios con datos oficiales"],
        ["Completar registros de tiempo con datos reales", "Cada integrante", "Crítica", "Fecha, horas, interrupción, delta y comentario completos"],
        ["Ejecutar muestra con dos usuarios", "Equipo", "Crítica", "Resultados y acciones registrados"],
        ["Realizar prueba manual de persistencia y recorrido", "Equipo", "Crítica", "Evidencia tras cerrar y reabrir el mundo"],
        ["Registrar y subir la reorganización a mods/", "Equipo", "Alta", "Workspace limpio y GitHub con rutas nuevas"],
        ["Revisar advertencias de compatibilidad con Gradle 10", "Equipo", "Baja", "Causas documentadas o scripts actualizados"],
    ], [3.3, 1.45, 0.75, 1.9], 8)
    add_heading(doc, "Lista de verificación para Canvas", 2)
    add_bullets(doc, [
        "Documento Word revisado, con cambios controlados y sin datos personales pendientes.",
        "Requisitos funcionales priorizados y tareas con horas, responsable y fecha.",
        "Investigación y decisión de persistencia incluidas.",
        "Formulario individual y reflexión de cada integrante.",
        "Resultados de usuarios finales y prueba manual incorporados.",
        "Vínculo del repositorio comprobado desde una sesión sin credenciales del autor.",
    ])
    add_note(doc, "Estado del documento", "El contenido técnico y la actividad de Git ya están incorporados. Solo quedan datos personales no publicados, horas reales, confirmación de reflexiones y sesiones con usuarios.", True)


def add_rubric_traceability(doc):
    add_heading(doc, "9. Trazabilidad con la rúbrica")
    add_table(doc, ["Criterio", "Puntos", "Ubicación en este documento", "Estado"], [
        ["Requisitos priorizados", "15", "Sección 1: prioridad, aceptación, responsable y estado", "Completo"],
        ["Planificación", "10", "Sección 3: T01–T17 y calendario", "Completo"],
        ["Investigación de persistencia", "7", "Sección 2: comparación, decisión, prueba y fuentes", "Completo"],
        ["Implementación", "40", "Sección 4: evidencia estática y pendientes manuales", "Parcial: falta prueba en juego"],
        ["Control de versiones", "20", "Sección 5: repositorio y actividad por autor", "Completo local; pendiente push"],
        ["Gestión", "8", "Secciones 3, 6, 7 y formularios individuales", "Parcial: faltan tiempos y usuarios reales"],
    ], [2.0, 0.65, 3.25, 1.5], 8)
    add_note(doc, "Uso de esta tabla", "Los estados parciales no deben cambiarse a completos hasta incorporar evidencia real. La tabla permite revisar la entrega sin confundir contenido preparado con actividad ejecutada.", True)


def build_document():
    doc = Document()
    configure_document(doc)
    props = doc.core_properties
    props.title = "Tercera entrega de UVG Minecraft Mods"
    props.subject = "Planificación, persistencia, implementación y gestión"
    props.author = "Equipo UVG Minecraft Mods"
    props.keywords = "CC2006, Minecraft, NeoForge, persistencia, entrega 3"

    add_cover(doc)
    add_team(doc)
    add_assignments(doc)
    add_summary(doc)
    add_requirements(doc)
    add_persistence(doc)
    add_planning(doc)
    add_implementation(doc)
    add_version_control(doc)
    add_user_review(doc)
    add_reflection(doc)
    estimated_time = {
        "Jacob Juárez Guerra": (
            "Por confirmar",
            [
                ("21/09/2026", "08:00", "09:30", "10", "80", "Scaffold University Guide", "Configuración de Gradle y NeoForge"),
                ("21/09/2026", "09:40", "12:10", "15", "135", "Recorridos guiados", "Entidad, red, pantallas, datos y comandos"),
                ("21/09/2026", "13:00", "15:00", "10", "110", "Señalización", "Clases Java y registros de Posted Signage"),
                ("21/09/2026", "15:20", "16:50", "10", "80", "Recursos", "Modelos, texturas y localización"),
                ("21/09/2026", "17:00", "18:30", "10", "80", "Builds", "Configuración de ambos proyectos"),
                ("21/09/2026", "19:00", "20:20", "5", "75", "Integración", "Entradas, registros y assets finales"),
            ],
        ),
        "Diego Marroquín": (
            "261402* (confirmar)",
            [
                ("21/09/2026", "08:30", "11:30", "15", "165", "Estado y navegación", "Lógica del recorrido y persistencia del guía"),
                ("21/09/2026", "12:30", "15:00", "10", "140", "Interfaz y render", "Pantallas, renderizadores y recursos"),
                ("22/09/2026", "09:00", "11:00", "10", "110", "Pruebas de recorrido", "Revisión de espera, llegada y ruta bloqueada"),
                ("24/09/2026", "14:00", "15:30", "5", "85", "Revisión técnica", "Comprobación de pantallas y reflexión individual"),
            ],
        ),
        "Oscar Cruz": (
            "Por confirmar",
            [
                ("21/09/2026", "08:00", "10:30", "10", "140", "Señales interactivas", "Señales de pared y letreros colgantes"),
                ("21/09/2026", "11:00", "14:00", "15", "165", "Decoración", "Macetero conectado y mural multiparte"),
                ("23/09/2026", "09:30", "11:30", "10", "110", "Pruebas de soporte", "Colocación, vecinos y eliminación"),
                ("24/09/2026", "16:00", "17:20", "5", "75", "Revisión técnica", "Comprobación del catálogo y reflexión"),
            ],
        ),
        "Ricardo Sandoval": (
            "Por confirmar",
            [
                ("21/09/2026", "08:00", "10:30", "10", "140", "Recursos de señales", "Seguridad, dirección y sanitarios"),
                ("21/09/2026", "11:00", "14:00", "15", "165", "Recursos decorativos", "Rótulos, macetero, mural y validador"),
                ("24/09/2026", "08:30", "10:00", "5", "85", "Reorganización", "Traslado de los mods al workspace compartido"),
                ("24/09/2026", "10:15", "11:45", "10", "80", "Compilación", "Herramienta para construir ambos módulos"),
                ("24/09/2026", "13:00", "16:00", "15", "165", "Documento de entrega", "Generador, revisión de rúbrica y evidencias"),
            ],
        ),
    }
    for name, (carnet, rows) in estimated_time.items():
        add_time_form(doc, name, carnet, rows)
    add_pending(doc)
    add_rubric_traceability(doc)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    doc.save(OUTPUT_PATH)
    print(OUTPUT_PATH)


if __name__ == "__main__":
    build_document()
