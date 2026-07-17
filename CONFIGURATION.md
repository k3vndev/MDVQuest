# Configuración de misiones

Las definiciones se leen desde cualquier `.yml` dentro de `plugins/MDVQuest/missions/`.

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
        - '&7• 1200 monedas'
      commands:
        - 'eco give %player% 1200'
      vanilla-items:
        - material: GOLD_INGOT
          amount: 2
      mmoitems:
        - type: MATERIAL
          id: COFRE_T2
          amount: 1
```

## Campos comunes de objetivo

- `amount`: progreso requerido.
- `name`: texto mostrado.
- `targets`: lista de materiales, entidades o IDs aceptados.
- `worlds`: mundos válidos para ese objetivo.
- `natural-only`: rechaza bloques colocados por jugadores.

## Campos por tipo

- `MINE_BLOCK`: `targets`, `natural-only`.
- `BREAK_CUSTOM_ORE`: `targets` con la clave interna de MDVHeadOres y opcional `resource-kind: ORE|TREE_NODE`.
- `CUT_LOG`: `targets` opcional; sin targets acepta cualquier `Tag.LOGS`.
- `HARVEST_CROP`: `targets`, `mature-only` (true por defecto), `natural-only` opcional.
- `KILL_VANILLA_MOB`: `targets` con EntityType.
- `KILL_MYTHIC_MOB`: `targets` con IDs internos exactos.
- `KILL_MOB_FAMILY`: `family` definida en `families.yml`, `include-minibosses`.
- `KILL_MINIBOSS`: `family` o `targets`; sin ambos acepta cualquier miniboss registrado.
- `CRAFT_VANILLA_ITEM`: `targets`, `count-produced-items`.
- `CRAFT_RECIPE`: `recipe` o `targets`, `count-produced-items`.
- `CRAFT_CATEGORY`: `category` o `targets`, `count-produced-items`.
- `OBTAIN_MMOITEM`: `mmoitems-type`, `mmoitems-id` o `targets: [TYPE:ID]`; opcional `sources: [CRAFT, CUSTOM_ORE, PICKUP, ADMIN]`.
- `DELIVER_MMOITEM`: `mmoitems-type`, `mmoitems-id` o `targets`.
- `DELIVER_VANILLA_ITEM`: `material` o `targets`.
- `USE_CONSUMABLE`: `material`/`targets` para vanilla, o `mmoitems-type` + `mmoitems-id` para MMOItems.
- `EARN_PROFESSION_EXP`: `profession` o `targets`.
- `COMPLETE_EVENT`: `event` o `targets`.
- `PLAYER_KILL`: `unique-victims` y `worlds` opcionales; además aplica la protección PvP global.

## Rotaciones

Cada rotación usa días reales y una fecha ancla. V1 limita `duration-days` a 1–7.

```yaml
rotations:
  three-days:
    enabled: true
    duration-days: 3
    mission-count: 2
    anchor-date: '2026-01-01'
    reset-time: '00:00'
    seed: 'mdvquest-three-days'
```

La selección es global, ponderada por `weight`, sin repetir una definición dentro del mismo ciclo. Si hay menos candidatas que `mission-count`, usa todas las disponibles.
