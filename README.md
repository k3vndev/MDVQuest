# MDVQuest 1.3.0

MDVQuest crea misiones individuales con rotaciones globales: durante cada ciclo todos los jugadores reciben las mismas misiones, pero el progreso y la reclamación pertenecen a cada jugador.

## Novedades de 1.3.0

- `/mdvquest`, `/quest`, `/quests`, `/misiones` y el botón de MDVSocial abren un menú **VIEW_ONLY**: permite revisar progreso, objetivos, recompensas y expiración, pero no abre detalles, no entrega objetos y no reclama premios.
- `/mdvquest npc [jugador]` abre el menú **INTERACTIVE** completo para el encargado de misiones o para pruebas administrativas.
- Ambos menús tienen configuración visual separada bajo `menus.viewer` y `menus.interactive`.
- El catálogo de `/mdvquest admin` puede filtrar misiones por archivo YAML; General/todos los YAML siempre aparece como primera opción.
- Las configuraciones visuales antiguas se copian automáticamente a ambas variantes en la primera carga de 1.3.0.

## Novedades de 1.2.5

- `mdvquest.editor` abre el catálogo y editor visual sin entregar recarga, reroll, force ni reportes administrativos.
- `mdvquest.admin` conserva la administración técnica completa y hereda el permiso de editor.

## Novedades de 1.2.4

- La cuadrícula del editor de recompensas queda realmente vacía en los slots 9–44; los paneles de relleno de MDVSocial ya no ocupan ni se guardan como premios.
- Shift-click desde el inventario añade stacks exclusivamente a la cuadrícula editable.
- Shift-click en la cuadrícula elimina una recompensa existente o devuelve una plantilla real al inventario.
- Los clicks normales, click derecho y arrastre permiten mover, separar y combinar objetos libremente dentro de los slots editables.
- Al guardar, la cuadrícula reemplaza el conjunto de recompensas de objetos; la EXP y los comandos se conservan.
- Mover una misión entre archivos YAML elimina la copia original, incluso cuando el ID original usaba guiones bajos.
- Al editar una misión, el guardado limpia duplicados históricos del mismo ID normalizado en otros YAML.

- `/mdvquest force <id>` añade una misión concreta al ciclo actual sin borrar progreso.
- Las misiones VIP bloqueadas se completan sin mostrar un aviso engañoso de reclamación.
- El icono de reclamación pendiente usa panel verde claro.

- Cantidad variable de misiones por rotación: cada pool usa `min-missions` y `max-missions`.
- Tres selecciones globales y estables por ciclo:
  - `normal`: solo definiciones normales.
  - `vip1`: definiciones normales restantes + definiciones VIP1.
  - `vip2`: definiciones VIP1 restantes + definiciones VIP2.
- No hay porcentajes por origen: todas las candidatas habilitadas dentro del pool participan juntas.
- Una misma definición no puede repetirse entre los tres pools durante el mismo ciclo.
- Todos los jugadores ven y progresan todas las misiones activas.
- Las recompensas VIP solo se pueden reclamar con `mdvquest.access.vip1` o `mdvquest.access.vip2`.
- Sin rango, las misiones VIP1 usan panel celeste y las VIP2 panel amarillo; el lore de bloqueo es editable.
- El editor visual permite asignar cada definición como Normal, VIP1 o VIP2.
- `/mdvquest reroll <rotación|all> confirmar` regenera selecciones con confirmación destructiva.
- Migración automática de SQLite: conserva progreso existente y añade `access_pool`.
- Editor visual completo: `/mdvquest crear`.
- Catálogo administrativo: `/mdvquest admin`.
- Edición de misiones existentes con clic derecho.
- Selector de duración de 1 a 7 días reales.
- Nombre, lore, icono exacto, peso individual, estado y archivo YAML editables in-game.
- Catálogo guiado de todos los objetivos de V1.
- Hasta 7 objetivos por misión por defecto, incluidos varios del mismo tipo.
- Recompensas físicas configurables depositando los objetos en una GUI.
- Identificación automática de objetos vanilla, MMOItems y MythicMobs/Crucible.
- Recompensas estructuradas de experiencia principal o profesiones MMOCore.
- Reclamación segura: calcula los stacks completos y exige slots vacíos antes de registrar el premio.
- Menú principal sin reloj ni botón de cerrar: volver en slot 49 y flechas extremas solo cuando hacen falta.
- Los libros muestran completadas/total y el tiempo hasta la próxima actualización del grupo.
- Detalle visual con objetivos 10–16, recompensas 29–33, marco verde y paginación independiente.
- `KILL_ANY_HOSTILE_MOB` para contar monstruos vanilla y MythicMobs.

## Flujo público

- El jugador no acepta misiones manualmente.
- Toda acción válida progresa automáticamente las misiones activas compatibles.
- `/quest` y MDVSocial permiten consultar el progreso desde cualquier lugar, sin acciones de entrega o reclamación.
- El NPC abre el menú interactivo con `/mdvquest npc <jugador>`.
- Al completar una misión, el visor indica que debe visitarse al encargado.
- En el menú interactivo, MDVQuest verifica el espacio antes de entregar la recompensa.
- Si la misión expira antes de reclamar, la recompensa se pierde.
- Al reclamar, queda marcada con tinte gris hasta el final del ciclo.

## Menús

Las dos variantes principales agrupan las misiones en 1 día, 2–3 días, 4–6 días y 7 días.

- `menus.viewer`: visor público de solo lectura.
- `menus.interactive`: menú completo del NPC.
- `menus.interactive.detail`: detalle, entregas y vista paginada de recompensas.

Cada variante permite configurar por separado título, tamaño, relleno, slots, categorías, separadores, estados, lore, paginación y botón de regreso. Las flechas solo aparecen cuando hacen falta. Los iconos ocultan atributos y tooltips vanilla innecesarios. MDVSocial continúa como `softdepend`: si está instalado, MDVQuest reutiliza sus inventarios, botones y sonidos; si no lo está, mantiene una GUI de respaldo.

## Nombres visibles de recompensas

- Los materiales vanilla se insertan como componentes traducibles, por lo que el cliente los muestra en su idioma (por ejemplo, español).
- MMOItems y MythicMobs/Crucible se construyen para leer su nombre efectivo real, no su ID interno.
- Los nombres de nivel principal y profesiones se personalizan en `rewards.profession-display-names`.

## Recompensas de ejemplo seguras

`missions/examples.yml` no entrega dinero: usa EXP principal baja y lingotes de hierro. En una actualización desde una copia vieja, `safety.sanitize-example-economy-rewards: true` migra únicamente ese archivo una vez; no toca tus otros YAML.

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

Permiso requerido: `mdvquest.editor` o `mdvquest.admin`.

- `/mdvquest crear`: comienza una misión nueva.
- `/mdvquest admin`: abre el catálogo completo.
- En el catálogo: clic izquierdo para visualizar; clic derecho para editar.
- El botón del slot 46 filtra por YAML: izquierdo siguiente, derecho anterior y Shift-click General.
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

- `/mdvquest`, `/misiones`, `/quests`, `/quest`: visor público de solo consulta.
- `/mdvquest npc [jugador]`: menú interactivo completo; desde consola/Citizens el jugador es obligatorio.
- `/mdvquest crear`: editor de misión nueva.
- `/mdvquest admin`: catálogo administrativo.
- `/mdvquest reload`: recarga configuraciones y misiones.
- `/mdvquest status`: muestra instancias activas.
- `/mdvquest reroll <rotación|all> confirmar`: regenera una rotación o todas y elimina su progreso/recompensas pendientes.
- `/mdvquest event <jugador> <evento> [cantidad]`: puente para eventos externos.
- `/mdvquest profexp <jugador> <profesión> <cantidad>`: puente de experiencia.
- `/mdvquest report <jugador> <tipo> <objetivo> <cantidad>`: reporte administrativo genérico.

## Compilación

Requiere Java 21. El proyecto usa `paper-api:1.21.6-R0.1-SNAPSHOT` como dependencia `provided` y empaqueta únicamente SQLite JDBC.

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.3.0.jar
```

El resultado queda en:

```text
target/MDVQuest-1.3.0.jar
```

GitHub Actions ejecuta ambos pasos y publica el JAR como artefacto. Consulta `BUILD-GITHUB.md` y prueba primero en staging con `TEST-CHECKLIST.md`.

## Actualización desde 1.1.1

- Reemplaza únicamente el JAR con el servidor apagado.
- Conserva `plugins/MDVQuest/`, sus YAML y `mdvquest.db`.
- Las misiones y recompensas antiguas por comandos continúan funcionando.
- Las nuevas claves se fusionan con `config.yml`; no borres tu configuración ni SQLite.
- SQLite añade automáticamente `access_pool`; las instancias antiguas se consideran normales.
- Al primer arranque se generan los pools VIP que falten para el ciclo actual, sin reemplazar las misiones normales existentes.
- El saneamiento opcional afecta únicamente a `missions/examples.yml`.
- Revisa `menus.viewer`, `menus.interactive` y `menus.interactive.detail` para personalizar las dos variantes.
- Las antiguas secciones `menus.main` y `menus.detail` se migran automáticamente sin borrar tu diseño.
