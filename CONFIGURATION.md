# Configuración de MDVQuest 1.2.4

Las misiones se leen desde todos los archivos `.yml` ubicados en:

```text
plugins/MDVQuest/missions/
```

El editor visual escribe en esa misma carpeta y permite elegir el archivo de destino.

## Ejemplo completo

```yaml
missions:
  abastecimiento_t2:
    enabled: true
    rotation: four-days
    weight: 10
    name: '&5Abastecimiento T2'
    icon: AMETHYST_SHARD
    lore:
      - '&7Completa todos los objetivos.'

    objectives:
      hueste:
        type: KILL_MOB_FAMILY
        family: HUESTE_INSEPULTA
        include-minibosses: false
        amount: 40
        name: 'Derrota miembros de la Hueste'

      receta:
        type: CRAFT_CATEGORY
        category: MATERIALES
        amount: 5
        count-produced-items: true
        name: 'Fabrica materiales'

      entrega:
        type: DELIVER_MMOITEM
        mmoitems-type: MATERIAL
        mmoitems-id: ESENCIA_FUNEBRE
        amount: 12
        name: 'Entrega Esencias Fúnebres'

    rewards:
      lore:
        - '&7Premio de abastecimiento T2.'
      experience:
        - profession: main
          amount: 500
        - profession: minero
          amount: 250
      vanilla-items:
        - material: GOLD_INGOT
          amount: 2
      mmoitems:
        - type: MATERIAL
          id: COFRE_T2
          amount: 1
      mythic-items:
        - id: RECOMPENSA_CRUCIBLE
          amount: 1
```

## Campos de misión

- `enabled`: participa o no en futuras selecciones.
- `rotation`: ID definido en `config.yml`.
- `weight`: peso relativo dentro del pool de esa rotación.
- `name`: nombre con colores `&`.
- `icon`: material de respaldo.
- `icon-item`: objeto Bukkit serializado por el editor para conservar modelo, nombre y meta exacta.
- `lore`: descripción.
- `objectives`: uno o varios objetivos. Todos deben completarse.
- `rewards`: recompensas manualmente reclamables.

El editor limita a 7 objetivos por misión por defecto, correspondientes a los slots 10–16. Se cambia con `editor.max-objectives-per-mission`, pero nunca supera la cantidad configurada en `menus.detail.objective-slots`.

## Objetivos

### MINE_BLOCK

```yaml
type: MINE_BLOCK
targets: [IRON_ORE, DEEPSLATE_IRON_ORE]
amount: 32
natural-only: true
```

`natural-only` rechaza bloques colocados por jugadores. Para mantener el registro económico, define los materiales cuando se usa esta opción.

### BREAK_CUSTOM_ORE

```yaml
type: BREAK_CUSTOM_ORE
targets: [VIRIDITA]
resource-kind: ORE
amount: 12
```

Usa IDs internos emitidos por MDVHeadOres. `resource-kind` puede ser `ORE`, `TREE_NODE` u otro valor del plugin emisor.

### CUT_LOG

```yaml
type: CUT_LOG
targets: [OAK_LOG, BIRCH_LOG]
amount: 40
natural-only: true
```

Sin `targets`, acepta cualquier bloque de `Tag.LOGS`.

### HARVEST_CROP

```yaml
type: HARVEST_CROP
targets: [WHEAT, CARROTS, POTATOES]
amount: 48
mature-only: true
natural-only: true
```

### KILL_VANILLA_MOB

```yaml
type: KILL_VANILLA_MOB
targets: [ZOMBIE, SKELETON, SPIDER]
amount: 20
```

### KILL_MYTHIC_MOB

```yaml
type: KILL_MYTHIC_MOB
targets: [SAURIO_ACECHADOR, SAURIO_LANCERO]
amount: 15
```

### KILL_MOB_FAMILY

```yaml
type: KILL_MOB_FAMILY
family: HUESTE_INSEPULTA
include-minibosses: false
amount: 50
```

La familia se define en `families.yml`.

### KILL_MINIBOSS

Por familia:

```yaml
type: KILL_MINIBOSS
family: HUESTE_INSEPULTA
amount: 2
```

Por IDs:

```yaml
type: KILL_MINIBOSS
targets: [NECROTIDO_FUERTE, SACERDOTE_FUNEBRE]
amount: 2
```

Sin familia ni targets, acepta cualquier miniboss registrado en `families.yml`.

### KILL_ANY_HOSTILE_MOB

```yaml
type: KILL_ANY_HOSTILE_MOB
amount: 50
```

Cuenta monstruos vanilla y muertes de MythicMobs reportadas por la integración.

### CRAFT_VANILLA_ITEM

```yaml
type: CRAFT_VANILLA_ITEM
targets: [IRON_INGOT, BREAD]
amount: 16
count-produced-items: true
```

### CRAFT_RECIPE

```yaml
type: CRAFT_RECIPE
recipe: NUCLEO_GELIDO_PULIDO
amount: 3
count-produced-items: true
```

También admite `targets` para varios IDs de MDVRecetas.

### CRAFT_CATEGORY

```yaml
type: CRAFT_CATEGORY
category: MATERIALES
amount: 8
count-produced-items: true
```

También admite `targets` para varias categorías.

### OBTAIN_MMOITEM

```yaml
type: OBTAIN_MMOITEM
targets: [MATERIAL:ESCAMA_SAURIA]
amount: 10
sources: [CRAFT, CUSTOM_ORE, PICKUP]
```

También acepta `mmoitems-type` y `mmoitems-id`. `sources` es opcional.

### DELIVER_MMOITEM

```yaml
type: DELIVER_MMOITEM
mmoitems-type: MATERIAL
mmoitems-id: ESCAMA_SAURIA
amount: 20
```

El jugador entrega parcial o totalmente desde el menú de detalle.

### DELIVER_VANILLA_ITEM

```yaml
type: DELIVER_VANILLA_ITEM
material: IRON_INGOT
amount: 32
```

### USE_CONSUMABLE

Vanilla:

```yaml
type: USE_CONSUMABLE
targets: [COOKED_BEEF]
amount: 8
```

MMOItems:

```yaml
type: USE_CONSUMABLE
targets: [CONSUMABLE:TONIFICADOR_T2]
amount: 3
```

### EARN_PROFESSION_EXP

```yaml
type: EARN_PROFESSION_EXP
profession: minero
amount: 500
```

También admite `targets` con varias profesiones.

### COMPLETE_EVENT

```yaml
type: COMPLETE_EVENT
event: ECLIPSE_LUNAR
amount: 1
```

Se reporta mediante la API o `/mdvquest event`.

### PLAYER_KILL

```yaml
type: PLAYER_KILL
amount: 3
unique-victims: true
worlds: [world]
```

También aplica las protecciones globales de `anti-exploit.pvp`.

## Recompensas

### Experiencia MMOCore

```yaml
experience:
  - profession: main
    amount: 500
  - profession: minero
    amount: 250
```

El comando se define en:

```yaml
rewards:
  mmocore-experience-command: 'mmocore admin exp give %player% %profession% %amount% false'
```

### Objetos vanilla

```yaml
vanilla-items:
  - material: DIAMOND
    amount: 2
```

### MMOItems

```yaml
mmoitems:
  - type: MATERIAL
    id: COFRE_T2
    amount: 1
```

### MythicMobs / Crucible

```yaml
mythic-items:
  - id: RECOMPENSA_CRUCIBLE
    amount: 1
```

El editor identifica el ID a partir del objeto depositado y el reclamo vuelve a construirlo mediante el ItemManager de Mythic.

### Objetos exactos

`exact-items` es generado por el editor cuando un objeto con meta no pertenece a MMOItems ni Mythic. Bukkit lo serializa dentro del YAML para conservar nombre, lore, modelo y demás metadatos compatibles.

### Comandos

```yaml
commands:
  - 'say %player% completó %mission%'
```

Placeholders propios: `%player%`, `%uuid%`, `%mission%` y `%rotation%`.

## Rotaciones reales y pools de acceso

Cada duración genera hasta tres selecciones globales. La cantidad se elige una sola vez por ciclo entre el mínimo y el máximo y permanece estable tras reinicios y `/mdvquest reload`.

```yaml
rotations:
  daily:
    enabled: true
    duration-days: 1
    pools:
      normal:
        min-missions: 5
        max-missions: 8
      vip1:
        min-missions: 2
        max-missions: 5
      vip2:
        min-missions: 1
        max-missions: 4
    anchor-date: '2026-01-01'
    reset-time: '00:00'
    seed: 'mdvquest-daily'
```

Reglas de selección:

- `normal`: solo definiciones con `access-pool: normal`.
- `vip1`: definiciones normales que no salieron antes + definiciones `vip1`.
- `vip2`: definiciones `vip1` que no salieron antes + definiciones `vip2`.
- No existen porcentajes 60/40 ni pesos por origen del pool.
- La misma definición no se repite entre pools en el mismo ciclo.
- El campo individual `weight` de cada misión sigue controlando su probabilidad relativa frente a las demás candidatas disponibles. Con el mismo peso, todas tienen la misma posibilidad.
- Si faltan candidatas, se seleccionan todas las disponibles y se informa en consola.

`duration-days` admite de 1 a 7 en V1. `anchor-date` y `reset-time` fijan los límites de los ciclos en la zona horaria configurada. `seed` hace estable la selección normal tras reinicios.

Las configuraciones antiguas que todavía tengan `mission-count` continúan funcionando como un único pool normal fijo; para activar cantidades variables y VIP conviene migrarlas al bloque `pools`.

### Acceso de cada definición

Dentro de una misión:

```yaml
missions:
  cazador_legendario:
    enabled: true
    rotation: daily
    access-pool: vip1
    weight: 10
```

Valores:

- `normal`: puede salir en el pool normal o como extra VIP1.
- `vip1`: puede salir en el pool VIP1 o como extra VIP2.
- `vip2`: solo puede salir en el pool VIP2.

El pool de la **instancia seleccionada** determina el permiso de reclamación. Por ejemplo, una definición normal elegida como extra VIP1 requiere el permiso VIP1 para reclamar esa instancia.

```yaml
access-tiers:
  vip2-inherits-vip1: true
  vip1:
    display-name: 'VIP'
    permission: 'mdvquest.access.vip1'
    locked-material: LIGHT_BLUE_STAINED_GLASS_PANE
  vip2:
    display-name: 'VIP 2'
    permission: 'mdvquest.access.vip2'
    locked-material: YELLOW_STAINED_GLASS_PANE
```

Todos pueden ver y progresar las instancias VIP. Sin el permiso correspondiente, el icono se reemplaza por el panel configurado y la recompensa no puede reclamarse. El aviso del lore se edita en:

```yaml
menus:
  main:
    access:
      locked-line: '&b● Necesitas el rango &f%rank% &bpara reclamar la recompensa de esta misión.'
```

Placeholders: `%rank%`, `%permission%` y `%pool%`.

### Reroll administrativo

```text
/mdvquest reroll daily confirmar
/mdvquest reroll two-days confirmar
/mdvquest reroll weekly confirmar
/mdvquest reroll all confirmar
```

El reroll conserva el final temporal del ciclo, pero elimina las instancias actuales de la rotación afectada, su progreso y las recompensas completadas sin reclamar. Después vuelve a sortear la cantidad y las misiones de los tres pools. Requiere `mdvquest.admin.reroll`.

## Cabeza de volver

```yaml
menus:
  back-head-texture: ''
```

Acepta una URL de `textures.minecraft.net` o un Value Base64 que contenga esa URL.

## Compatibilidad con configuraciones 1.0.x

Las recompensas antiguas con `commands`, `vanilla-items` y `mmoitems` siguen siendo válidas. No hace falta convertirlas para arrancar 1.2.4.


## Personalización de los menús públicos

La distribución completa está en `config.yml`. Los slots usan numeración Bukkit de `0` a `53`.

### Catálogo principal

```yaml
menus:
  main:
    category-slots: [9, 18, 27, 36]
    mission-slots: [10,11,12,13,14,15,16,17,19,20,21,22,23,24,25,26,28,29,30,31,32,33,34,35,37,38,39,40,41,42,43,44]
    previous-page-slot: 45
    back-slot: 49
    next-page-slot: 53
```

Los placeholders de los libros son `%completed%`, `%total%` y `%remaining%`. Para 2–3 y 4–6 días, `%remaining%` usa la rotación que vaya a actualizarse primero.

### Detalle de misión

```yaml
menus:
  detail:
    mission-icon-slot: 4
    objective-slots: [10,11,12,13,14,15,16]
    reward-slots: [29,30,31,32,33]
    previous-reward-page-slot: 45
    back-slot: 49
    next-reward-page-slot: 53
    reward-border:
      enabled: true
      material: LIME_STAINED_GLASS_PANE
      slots: [19,20,21,22,23,24,25,28,34,37,38,39,40,41,42,43]
```

Las flechas no se colocan cuando solo existe una página. El botón de cerrar fue eliminado; el jugador puede cerrar con la tecla normal de inventario.

## Nombres de objetos y profesiones

Los objetos vanilla usan su componente traducible del cliente. Los objetos MMOItems y MythicMobs/Crucible muestran el nombre efectivo del objeto construido. Para experiencia de MMOCore:

```yaml
rewards:
  profession-display-names:
    main: "Nivel principal"
    mining: "Minería"
    woodcutting: "Leñador"
```

## Seguridad de los ejemplos

```yaml
safety:
  sanitize-example-economy-rewards: true
```

Esta migración se ejecuta una vez sobre `missions/examples.yml`, reemplazando sus recompensas por EXP principal baja y hierro. No modifica otros archivos. Desactívala antes del primer arranque de 1.2.4 si reutilizas `examples.yml` como archivo de producción y no quieres que sea saneado.
