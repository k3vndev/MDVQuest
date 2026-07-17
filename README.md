# MDVQuest 1.0.3

## Compilacion oficial

Este repositorio debe compilarse con **Java 21** y la dependencia oficial de Paper 1.21.6 declarada en `pom.xml`. No instales JAR de Bukkit/Paper hechos a mano en el repositorio local de Maven.

```bash
mvn -B -U clean package
bash scripts/verify-bytecode.sh target/MDVQuest-1.0.3.jar
```

El JAR final aparece en `target/MDVQuest-1.0.3.jar`. El workflow `.github/workflows/build.yml` hace estos mismos pasos en GitHub Actions y publica el JAR como artefacto.

MDVQuest 1.0.3 implementa misiones individuales de rotación global: durante un ciclo todos los jugadores reciben las mismas misiones, pero cada uno conserva su propio progreso y reclamación.

## Comportamiento principal

- Progreso automático; no se aceptan misiones manualmente.
- Recompensa manual desde `/misiones` o el botón instalado en MDVSocial.
- Si una misión expira sin reclamar, el progreso y la recompensa se eliminan.
- Duraciones de 1 a 7 días reales, según `America/Argentina/Cordoba` por defecto.
- Una misión puede contener uno o varios objetivos.
- Selección global ponderada y determinista por ciclo: reiniciar el servidor no cambia las misiones activas.
- SQLite único y rotativo; solo se crean filas cuando un jugador progresa o reclama.
- Procesamiento por eventos. No escanea inventarios, mundos, jugadores ni clanes periódicamente.

## Integraciones incluidas

- MDVSocial 1.4.0 como núcleo visual opcional (`softdepend`), con GUI propia mínima como respaldo.
- MDVRecetas 0.6.11 mediante `MDVRecipeCraftEvent` para crafteos reales, incluyendo hornos y cantidades producidas.
- MDVHeadOres 1.0.8 mediante `MDVResourceBreakEvent` para vetas/nodos realmente extraídos, drops y XP concedida.
- MythicMobs mediante evento/fallback por API y deduplicación por UUID de entidad.
- MMOItems para identificar, entregar, obtener, consumir y recompensar TYPE+ID.
- MMOCore mediante XP reportada por MDVHeadOres y el puente público/API de MDVQuest.
- PlaceholderAPI opcional para negar bajas PvP contra miembros del mismo clan.

## Objetivos de V1

`MINE_BLOCK`, `BREAK_CUSTOM_ORE`, `CUT_LOG`, `HARVEST_CROP`, `KILL_VANILLA_MOB`, `KILL_MYTHIC_MOB`, `KILL_MOB_FAMILY`, `KILL_MINIBOSS`, `CRAFT_VANILLA_ITEM`, `CRAFT_RECIPE`, `CRAFT_CATEGORY`, `OBTAIN_MMOITEM`, `DELIVER_MMOITEM`, `DELIVER_VANILLA_ITEM`, `USE_CONSUMABLE`, `EARN_PROFESSION_EXP`, `COMPLETE_EVENT` y `PLAYER_KILL`.

`CLAN_KILL` está reservado y rechazado en V1 para evitar mezclar antes de tiempo la arquitectura de MDVQuest V2.

## Comandos

- `/mdvquest`, `/misiones`, `/quests` o `/quest`: abre el menú.
- `/mdvquest status`: muestra instancias activas.
- `/mdvquest reload`: recarga configuración y definiciones.
- `/mdvquest rotate <rotación>`: fuerza una nueva selección global para ese ciclo.
- `/mdvquest event <jugador> <evento> [cantidad]`: puente para MDVSpawns/eventos.
- `/mdvquest profexp <jugador> <profesión> <cantidad>`: puente para XP externa.
- `/mdvquest report <jugador> <tipo> <objetivo> <cantidad>`: puente administrativo genérico.

## API pública

```java
MDVQuestAPI.report(player, ObjectiveType.COMPLETE_EVENT, "ECLIPSE_LUNAR", 1);
MDVQuestAPI.reportProfessionExperience(player, "minero", 25);
MDVQuestAPI.openMenu(player);
```

Para integraciones nuevas, usa `MDVQuestAPI.report(...)` en el momento exacto en que la acción fue validada. No hagas escaneos periódicos.