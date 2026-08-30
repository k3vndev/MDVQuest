# MDVQuest 1.4.5 - Menús nativos Bedrock

Esta versión añade una adaptación exclusiva para jugadores Floodgate/Bedrock de los dos menús públicos de MDVQuest.

## Bedrock

- `/quest`, `/quests`, `/misiones` y `/mdvquest` abren un **Form nativo** con los contratos aceptados y su progreso.
- `/mdvquest npc [jugador]` abre un **Form nativo interactivo** para:
  - aceptar contratos;
  - entregar todos los objetos pendientes de una misión;
  - reclamar recompensas completadas;
  - cancelar contratos con confirmación.
- Las categorías de 1 día, 2-3 días, 4-6 días y 7 días se conservan.
- Hay paginación reducida automáticamente en dispositivos táctiles.
- Los detalles muestran descripción, progreso de todos los objetivos, recompensas, estado y tiempo restante.

## Configuración

Los textos de los Forms se generan y autoactualizan sin sobrescribir personalizaciones en:

- `plugins/MDVQuest/MenusBedrock/quest_viewer.yml`
- `plugins/MDVQuest/MenusBedrock/quest_npc.yml`

Ajustes generales:

```yaml
bedrock:
  enabled: true
  page-size: 6
  mobile-page-size: 5
  navigation-delay-ticks: 1
```

## Java

No se cambió el comportamiento de los inventarios Java. Si el jugador no es Floodgate, MDVQuest usa exactamente `QuestMenuManager` como antes.
