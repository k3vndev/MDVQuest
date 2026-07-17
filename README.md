# MDVQuest 1.1.0

MDVQuest crea misiones individuales con rotaciones globales: durante cada ciclo todos los jugadores reciben las mismas misiones, pero el progreso y la reclamación pertenecen a cada jugador.

## Novedades de 1.1.0

- Editor visual completo: `/mdvquest crear`.
- Catálogo administrativo: `/mdvquest admin`.
- Edición de misiones existentes con clic derecho.
- Selector de duración de 1 a 7 días reales.
- Nombre, lore, icono exacto, peso, estado y archivo YAML editables in-game.
- Catálogo guiado de todos los objetivos de V1.
- Hasta 9 objetivos por misión, incluidos varios del mismo tipo.
- Recompensas físicas configurables depositando los objetos en una GUI.
- Identificación automática de objetos vanilla, MMOItems y MythicMobs/Crucible.
- Recompensas estructuradas de experiencia principal o profesiones MMOCore.
- Reclamación segura: calcula los stacks completos y exige slots vacíos antes de registrar el premio.
- Rediseño de los menús públicos por grupos de duración.
- Detalle visual con objetivos, objetos y experiencia; recompensas paginadas.
- `KILL_ANY_HOSTILE_MOB` para contar monstruos vanilla y MythicMobs.

## Flujo público

- El jugador no acepta misiones manualmente.
- Toda acción válida progresa automáticamente las misiones activas compatibles.
- Al completar una misión, su icono cambia a lana verde.
- Al pulsarla, MDVQuest verifica el espacio y entrega la recompensa.
- Si la misión expira antes de reclamar, la recompensa se pierde.
- Al reclamar, queda marcada con tinte gris hasta el final del ciclo.

## Menús

El menú principal agrupa las misiones en:

- 1 día.
- 2 a 3 días.
- 4 a 6 días.
- 7 días.

Las flechas solo aparecen cuando la categoría o las recompensas necesitan otra página. Los iconos ocultan los atributos y tooltips vanilla innecesarios. MDVSocial continúa como `softdepend`: si está instalado, MDVQuest reutiliza sus inventarios, botones y sonidos; si no lo está, mantiene una GUI de respaldo.

## Reclamación segura

Los objetos se construyen antes de registrar la reclamación. MDVQuest:

1. Valida todos los IDs configurados.
2. Divide cantidades grandes en stacks reales.
3. Cuenta los slots vacíos del inventario de almacenamiento.
4. Si faltan slots, cierra el menú y muestra cuántos debe liberar el jugador.
5. Si hay espacio, reserva esos slots y registra la reclamación de forma atómica en SQLite.
6. En el tick de entrega, cualquier objeto ajeno colocado en un slot reservado se expulsa al suelo y la recompensa ocupa ese slot.

No se arrojan recompensas por falta de espacio ni se marca la misión como reclamada antes de superar la validación.

## Editor visual

Permiso requerido: `mdvquest.admin`.

- `/mdvquest crear`: comienza una misión nueva.
- `/mdvquest admin`: abre el catálogo completo.
- En el catálogo: clic izquierdo para visualizar; clic derecho para editar.
- En el editor: el icono superior muestra cómo va quedando la misión.
- El botón de objetivos abre el catálogo; `Shift + clic derecho` abre su administración.
- En la administración de objetivos: clic izquierdo edita y clic derecho elimina.
- Las respuestas guiadas se escriben en el chat. `cancelar` vuelve sin aplicar ese paso.
- El editor permite elegir cualquier `.yml` dentro de `plugins/MDVQuest/missions/` o crear uno nuevo.
- Guardar modifica únicamente la misión seleccionada y recarga MDVQuest.

El menú de recompensas devuelve al administrador todos los objetos depositados; solo guarda su identidad y cantidad en la misión. Los objetos nuevos se añaden a las recompensas actuales. El botón de limpiar elimina las recompensas físicas ya configuradas.

## Objetivos V1

`MINE_BLOCK`, `BREAK_CUSTOM_ORE`, `CUT_LOG`, `HARVEST_CROP`, `KILL_VANILLA_MOB`, `KILL_MYTHIC_MOB`, `KILL_MOB_FAMILY`, `KILL_MINIBOSS`, `KILL_ANY_HOSTILE_MOB`, `CRAFT_VANILLA_ITEM`, `CRAFT_RECIPE`, `CRAFT_CATEGORY`, `OBTAIN_MMOITEM`, `DELIVER_MMOITEM`, `DELIVER_VANILLA_ITEM`, `USE_CONSUMABLE`, `EARN_PROFESSION_EXP`, `COMPLETE_EVENT` y `PLAYER_KILL`.

`CLAN_KILL` permanece reservado para MDVQuest V2.

## Integraciones

- MDVSocial: núcleo visual opcional.
- MDVRecetas 0.6.11+: evento público de crafteo válido.
- MDVHeadOres 1.0.8+: evento público de recurso realmente extraído.
- MMOItems: detección y construcción por `TYPE:ID`.
- MythicMobs/Crucible: detección y construcción reflectiva desde el ItemManager de Mythic.
- MythicMobs: IDs, familias, minibosses y objetivo hostil general.
- MMOCore: experiencia principal o de profesión mediante comando configurable.
- PlaceholderAPI: protección opcional de bajas entre miembros del mismo clan.

## Rendimiento

- Un único SQLite.
- Instancias de misión globales por ciclo.
- Filas individuales creadas solo cuando existe progreso o reclamación.
- Índice en memoria por tipo de objetivo.
- Progreso impulsado por eventos; no escanea periódicamente inventarios, mobs, mundos ni jugadores.
- Escrituras agrupadas, con guardado inmediato al completar o reclamar.
- Limpieza rotativa de ciclos expirados y vacuum incremental.

## Comandos

- `/mdvquest`, `/misiones`, `/quests`, `/quest`: menú público.
- `/mdvquest crear`: editor de misión nueva.
- `/mdvquest admin`: catálogo administrativo.
- `/mdvquest reload`: recarga configuraciones y misiones.
- `/mdvquest status`: muestra instancias activas.
- `/mdvquest rotate <rotación>`: fuerza una nueva selección global.
- `/mdvquest event <jugador> <evento> [cantidad]`: puente para eventos externos.
- `/mdvquest profexp <jugador> <profesión> <cantidad>`: puente de experiencia.
- `/mdvquest report <jugador> <tipo> <objetivo> <cantidad>`: reporte administrativo genérico.

## Compilación

Requiere Java 21. El proyecto usa `paper-api:1.21.6-R0.1-SNAPSHOT` como dependencia `provided` y empaqueta únicamente SQLite JDBC.

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.1.0.jar
```

El resultado queda en:

```text
target/MDVQuest-1.1.0.jar
```

GitHub Actions ejecuta ambos pasos y publica el JAR como artefacto. Consulta `BUILD-GITHUB.md` y prueba primero en staging con `TEST-CHECKLIST.md`.

## Actualización desde 1.0.3

- Reemplaza únicamente el JAR con el servidor apagado.
- Conserva `plugins/MDVQuest/`, sus YAML y `mdvquest.db`.
- Las misiones y recompensas antiguas por comandos continúan funcionando.
- Las nuevas claves tienen valores de respaldo, por lo que no es obligatorio borrar `config.yml`.
- Para copiar las nuevas secciones comentadas, compáralo con `src/main/resources/config.yml`.
